package com.hugosol.webagent.agent.common;

import com.hugosol.webagent.service.LlmCallLogService;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class TaskRunner {

    private static final Logger log = LoggerFactory.getLogger(TaskRunner.class);

    private final ChatLanguageModel chatModel;
    private final LlmCallLogService logService;
    private final Map<TaskName, TaskDefinition<?, ?>> registry = new ConcurrentHashMap<>();

    public TaskRunner(ChatLanguageModel chatModel, LlmCallLogService logService) {
        this.chatModel = chatModel;
        this.logService = logService;
    }

    public void register(TaskName name, TaskDefinition<?, ?> definition) {
        if (registry.containsKey(name)) {
            throw new IllegalStateException("Task already registered: " + name);
        }
        registry.put(name, definition);
    }

    @SuppressWarnings("unchecked")
    public <P, R> R execute(TaskName name, P params, TaskContext ctx) {
        TaskDefinition<P, R> def = (TaskDefinition<P, R>) registry.get(name);
        if (def == null) {
            throw new IllegalStateException("No task registered for: " + name);
        }

        Map<String, String> placeholders = def.paramBuilder().apply(params);
        String prompt = fillTemplate(def.template(), placeholders);

        long startTime = System.currentTimeMillis();
        String response;
        try {
            response = chatModel.chat(prompt);
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

    private String fillTemplate(String template, Map<String, String> placeholders) {
        String result = template;
        for (var entry : placeholders.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return result;
    }
}
