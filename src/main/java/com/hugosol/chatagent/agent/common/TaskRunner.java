package com.hugosol.chatagent.agent.common;

import com.hugosol.chatagent.service.LlmCallLogService;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class TaskRunner {

    private static final Logger log = LoggerFactory.getLogger(TaskRunner.class);

    private final ChatLanguageModel defaultChatModel;
    private final ChatLanguageModel reportChatModel;
    private final LlmCallLogService logService;
    private final Map<TaskName, TaskDefinition<?, ?>> registry = new ConcurrentHashMap<>();

    public TaskRunner(@Qualifier("chatLanguageModel") ChatLanguageModel defaultChatModel,
                      @Qualifier("reportChatLanguageModel") ChatLanguageModel reportChatModel,
                      LlmCallLogService logService) {
        this.defaultChatModel = defaultChatModel;
        this.reportChatModel = reportChatModel;
        this.logService = logService;
    }

    public void register(TaskName name, TaskDefinition<?, ?> definition) {
        if (registry.containsKey(name)) {
            throw new IllegalStateException("Task already registered: " + name);
        }
        registry.put(name, definition);
    }

    @SuppressWarnings("unchecked")
    public <P, R> R requestModel(TaskName name, P params, TaskContext ctx) {
        TaskDefinition<P, R> def = (TaskDefinition<P, R>) registry.get(name);
        if (def == null) {
            throw new IllegalStateException("No task registered for: " + name);
        }

        Map<String, String> placeholders = def.paramBuilder().apply(params);
        String prompt = fillTemplate(def.template(), placeholders);

        ChatLanguageModel model = (name == TaskName.REPORT) ? reportChatModel : defaultChatModel;

        long startTime = System.currentTimeMillis();
        String response;
        try {
            response = model.chat(prompt);
        } catch (RuntimeException e) {
            long duration = System.currentTimeMillis() - startTime;
            logService.saveAsync(
                    ctx.sessionId(), ctx.userId(), name.name(), ctx.mode(),
                    prompt, null, null,
                    null,
                    null, null, duration, "ERROR", e.getMessage());
            log.warn("TaskRunner: task {} LLM call failed", name, e);

            if (def.errorStrategy() == ErrorStrategy.SWALLOW) {
                return null;
            }
            throw e;
        }

            long duration = System.currentTimeMillis() - startTime;
        logService.saveAsync(
                ctx.sessionId(), ctx.userId(), name.name(), ctx.mode(),
                prompt, null, null,
                response,
                null, null, duration, "SUCCESS", null);

        try {
            return def.parser().apply(response);
        } catch (Exception e) {
            log.warn("TaskRunner: task {} parse failed, response body: {}", name, response, e);

            if (def.errorStrategy() == ErrorStrategy.SWALLOW) {
                return null;
            }
            throw e instanceof RuntimeException re ? re : new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    public <P, R> R requestModel(TaskName name, P params, TaskContext ctx, String template) {
        TaskDefinition<P, R> def = (TaskDefinition<P, R>) registry.get(name);
        if (def == null) {
            throw new IllegalStateException("No task registered for: " + name);
        }

        Map<String, String> placeholders = def.paramBuilder().apply(params);
        String prompt = fillTemplate(template, placeholders);

        ChatLanguageModel model = (name == TaskName.REPORT) ? reportChatModel : defaultChatModel;

        long startTime = System.currentTimeMillis();
        String response;
        try {
            response = model.chat(prompt);
        } catch (RuntimeException e) {
            long duration = System.currentTimeMillis() - startTime;
            logService.saveAsync(
                    ctx.sessionId(), ctx.userId(), name.name(), ctx.mode(),
                    prompt, null, null,
                    null,
                    null, null, duration, "ERROR", e.getMessage());
            log.warn("TaskRunner: task {} LLM call failed", name, e);

            if (def.errorStrategy() == ErrorStrategy.SWALLOW) {
                return null;
            }
            throw e;
        }

        long duration = System.currentTimeMillis() - startTime;
        logService.saveAsync(
                ctx.sessionId(), ctx.userId(), name.name(), ctx.mode(),
                prompt, null, null,
                response,
                null, null, duration, "SUCCESS", null);

        try {
            return def.parser().apply(response);
        } catch (Exception e) {
            log.warn("TaskRunner: task {} parse failed, response body: {}", name, response, e);

            if (def.errorStrategy() == ErrorStrategy.SWALLOW) {
                return null;
            }
            throw e instanceof RuntimeException re ? re : new RuntimeException(e);
        }
    }

    /** Returns the raw LLM response string, without applying the parser. */
    @SuppressWarnings("unchecked")
    public <P> String requestRaw(TaskName name, P params, TaskContext ctx) {
        TaskDefinition<P, ?> def = (TaskDefinition<P, ?>) registry.get(name);
        if (def == null) {
            throw new IllegalStateException("No task registered for: " + name);
        }

        Map<String, String> placeholders = def.paramBuilder().apply(params);
        String prompt = fillTemplate(def.template(), placeholders);

        ChatLanguageModel model = (name == TaskName.REPORT) ? reportChatModel : defaultChatModel;

        long startTime = System.currentTimeMillis();
        String response;
        try {
            response = model.chat(prompt);
        } catch (RuntimeException e) {
            long duration = System.currentTimeMillis() - startTime;
            logService.saveAsync(
                    ctx.sessionId(), ctx.userId(), name.name(), ctx.mode(),
                    prompt, null, null,
                    null,
                    null, null, duration, "ERROR", e.getMessage());
            log.warn("TaskRunner: task {} LLM call failed", name, e);
            throw e;
        }

        long duration = System.currentTimeMillis() - startTime;
        logService.saveAsync(
                ctx.sessionId(), ctx.userId(), name.name(), ctx.mode(),
                prompt, null, null,
                response,
                null, null, duration, "SUCCESS", null);

        return response;
    }

    private String fillTemplate(String template, Map<String, String> placeholders) {
        String result = template;
        for (var entry : placeholders.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return result;
    }
}
