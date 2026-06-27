package com.hugosol.chatagent.agent.common;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hugosol.chatagent.service.LlmCallLogService;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class LlmReqConstructor {

    private static final Logger log = LoggerFactory.getLogger(LlmReqConstructor.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final ChatLanguageModel defaultChatModel;
    private final ChatLanguageModel reportChatModel;
    private final LlmCallLogService logService;
    private final Map<TaskName, LlmTaskDefinition<?, ?>> registry = new ConcurrentHashMap<>();

    public LlmReqConstructor(@Qualifier("chatLanguageModel") ChatLanguageModel defaultChatModel,
                             @Qualifier("reportChatLanguageModel") ChatLanguageModel reportChatModel,
                             LlmCallLogService logService) {
        this.defaultChatModel = defaultChatModel;
        this.reportChatModel = reportChatModel;
        this.logService = logService;
    }

    public void register(TaskName name, LlmTaskDefinition<?, ?> definition) {
        if (registry.containsKey(name)) {
            throw new IllegalStateException("Task already registered: " + name);
        }
        registry.put(name, definition);
    }

    // ── 3-param execute ──────────────────────────────────────────

    @SuppressWarnings("unchecked")
    public <P, R> R execute(TaskName name, P params, TaskContext ctx) {
        LlmTaskDefinition<P, R> def = (LlmTaskDefinition<P, R>) registry.get(name);
        if (def == null) {
            throw new IllegalStateException("No task registered for: " + name);
        }
        return doExecute(name, params, ctx, def.systemTemplate(), def);
    }

    // ── 4-param execute with system template override ─────────────

    @SuppressWarnings("unchecked")
    public <P, R> R execute(TaskName name, P params, TaskContext ctx, String systemTemplateOverride) {
        LlmTaskDefinition<P, R> def = (LlmTaskDefinition<P, R>) registry.get(name);
        if (def == null) {
            throw new IllegalStateException("No task registered for: " + name);
        }
        return doExecute(name, params, ctx, systemTemplateOverride, def);
    }

    // ── executeRaw — returns raw LLM response, no parser ──────────

    @SuppressWarnings("unchecked")
    public <P> String executeRaw(TaskName name, P params, TaskContext ctx) {
        LlmTaskDefinition<P, ?> def = (LlmTaskDefinition<P, ?>) registry.get(name);
        if (def == null) {
            throw new IllegalStateException("No task registered for: " + name);
        }
        return doExecuteRaw(name, params, ctx, def.systemTemplate(), def);
    }

    // ── chat — direct call without registration ──────────────────

    public enum ModelType { DEFAULT, REPORT }

    public String chat(List<ChatMessage> messages, TaskContext ctx,
                        String agentType, ModelType modelType) {
        ChatLanguageModel model = (modelType == ModelType.REPORT) ? reportChatModel : defaultChatModel;
        String systemPrompt = messages.isEmpty() ? null
                : (messages.get(0) instanceof SystemMessage sm ? sm.text() : null);

        long startTime = System.currentTimeMillis();
        Response<AiMessage> response;
        try {
            response = model.generate(messages);
        } catch (RuntimeException e) {
            long duration = System.currentTimeMillis() - startTime;
            logService.saveAsync(
                    ctx.sessionId(), ctx.userId(), agentType, ctx.mode(),
                    null, systemPrompt, serializeChatHistory(messages),
                    null, null, null, duration, "ERROR", e.getMessage());
            throw e;
        }

        long duration = System.currentTimeMillis() - startTime;
        String responseText = response.content().text();
        Integer inputTokens = response.tokenUsage() != null ? response.tokenUsage().inputTokenCount() : null;
        Integer outputTokens = response.tokenUsage() != null ? response.tokenUsage().outputTokenCount() : null;

        logService.saveAsync(
                ctx.sessionId(), ctx.userId(), agentType, ctx.mode(),
                null, systemPrompt, serializeChatHistory(messages),
                responseText, inputTokens, outputTokens, duration, "SUCCESS", null);

        return responseText;
    }

    // ── Core execution ───────────────────────────────────────────

    private <P, R> R doExecute(TaskName name, P params, TaskContext ctx,
                                String systemTemplate, LlmTaskDefinition<P, R> def) {
        Map<String, String> placeholders = def.paramBuilder().apply(params);

        List<ChatMessage> messages = assembleMessages(systemTemplate, def.userTemplate(),
                def.exampleMessages(), placeholders);

        ChatLanguageModel model = selectModel(name);
        String systemPrompt = fillTemplate(systemTemplate, placeholders);

        long startTime = System.currentTimeMillis();
        Response<AiMessage> response;
        try {
            response = model.generate(messages);
        } catch (RuntimeException e) {
            long duration = System.currentTimeMillis() - startTime;
            logService.saveAsync(
                    ctx.sessionId(), ctx.userId(), name.name(), ctx.mode(),
                    null, systemPrompt, serializeChatHistory(messages),
                    null, null, null, duration, "ERROR", e.getMessage());
            log.warn("LlmReqConstructor: task {} LLM call failed", name, e);

            if (def.errorStrategy() == ErrorStrategy.SWALLOW) {
                return null;
            }
            throw e;
        }

        long duration = System.currentTimeMillis() - startTime;
        String responseText = response.content().text();
        Integer inputTokens = response.tokenUsage() != null ? response.tokenUsage().inputTokenCount() : null;
        Integer outputTokens = response.tokenUsage() != null ? response.tokenUsage().outputTokenCount() : null;

        logService.saveAsync(
                ctx.sessionId(), ctx.userId(), name.name(), ctx.mode(),
                null, systemPrompt, serializeChatHistory(messages),
                responseText, inputTokens, outputTokens, duration, "SUCCESS", null);

        try {
            return def.parser().apply(responseText);
        } catch (Exception e) {
            log.warn("LlmReqConstructor: task {} parse failed, response body: {}", name, responseText, e);

            if (def.errorStrategy() == ErrorStrategy.SWALLOW) {
                return null;
            }
            throw e instanceof RuntimeException re ? re : new RuntimeException(e);
        }
    }

    private <P> String doExecuteRaw(TaskName name, P params, TaskContext ctx,
                                     String systemTemplate, LlmTaskDefinition<P, ?> def) {
        Map<String, String> placeholders = def.paramBuilder().apply(params);

        List<ChatMessage> messages = assembleMessages(systemTemplate, def.userTemplate(),
                def.exampleMessages(), placeholders);

        ChatLanguageModel model = selectModel(name);
        String systemPrompt = fillTemplate(systemTemplate, placeholders);

        long startTime = System.currentTimeMillis();
        Response<AiMessage> response;
        try {
            response = model.generate(messages);
        } catch (RuntimeException e) {
            long duration = System.currentTimeMillis() - startTime;
            logService.saveAsync(
                    ctx.sessionId(), ctx.userId(), name.name(), ctx.mode(),
                    null, systemPrompt, serializeChatHistory(messages),
                    null, null, null, duration, "ERROR", e.getMessage());
            log.warn("LlmReqConstructor: task {} LLM call failed", name, e);
            throw e;
        }

        long duration = System.currentTimeMillis() - startTime;
        String responseText = response.content().text();
        Integer inputTokens = response.tokenUsage() != null ? response.tokenUsage().inputTokenCount() : null;
        Integer outputTokens = response.tokenUsage() != null ? response.tokenUsage().outputTokenCount() : null;

        logService.saveAsync(
                ctx.sessionId(), ctx.userId(), name.name(), ctx.mode(),
                null, systemPrompt, serializeChatHistory(messages),
                responseText, inputTokens, outputTokens, duration, "SUCCESS", null);

        return responseText;
    }

    // ── Message assembly ─────────────────────────────────────────
    // Order: SystemMessage → exampleMessages → UserMessage

    List<ChatMessage> assembleMessages(String systemTemplate, String userTemplate,
                                        List<ChatMessage> exampleMessages,
                                        Map<String, String> placeholders) {
        List<ChatMessage> messages = new ArrayList<>();

        messages.add(new SystemMessage(fillTemplate(systemTemplate, placeholders)));

        if (exampleMessages != null) {
            messages.addAll(exampleMessages);
        }

        messages.add(new UserMessage(fillTemplate(userTemplate, placeholders)));

        return messages;
    }

    // ── Helpers ──────────────────────────────────────────────────

    private ChatLanguageModel selectModel(TaskName name) {
        return (name == TaskName.REPORT) ? reportChatModel : defaultChatModel;
    }

    String fillTemplate(String template, Map<String, String> placeholders) {
        String result = template;
        for (var entry : placeholders.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return result;
    }

    private String serializeChatHistory(List<ChatMessage> messages) {
        try {
            // Serialize UserMessage and AiMessage items (skip SystemMessage)
            List<Object> history = new ArrayList<>();
            for (ChatMessage msg : messages) {
                if (msg instanceof UserMessage um) {
                    history.add(Map.of("role", "user", "content", um.singleText()));
                } else if (msg instanceof AiMessage am) {
                    history.add(Map.of("role", "assistant", "content", am.text()));
                }
            }
            return objectMapper.writeValueAsString(history);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize chat history", e);
            return "[]";
        }
    }
}
