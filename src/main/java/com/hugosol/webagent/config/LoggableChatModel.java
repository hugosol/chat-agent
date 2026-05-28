package com.hugosol.webagent.config;

import com.hugosol.webagent.service.LlmCallLogService;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class LoggableChatModel implements ChatLanguageModel {

    private static final Logger log = LoggerFactory.getLogger(LoggableChatModel.class);

    private final ChatLanguageModel delegate;
    private final LlmCallLogService logService;

    public LoggableChatModel(ChatLanguageModel delegate, LlmCallLogService logService) {
        this.delegate = delegate;
        this.logService = logService;
    }

    @Override
    public String chat(String prompt) {
        long startTime = System.currentTimeMillis();
        try {
            String response = delegate.chat(prompt);
            long duration = System.currentTimeMillis() - startTime;
            logService.saveAsync(null, null, null, null,
                    escapeAndWrap(prompt), prompt, null,
                    response,
                    null, null, duration, "SUCCESS", null);
            return response;
        } catch (RuntimeException e) {
            long duration = System.currentTimeMillis() - startTime;
            logService.saveAsync(null, null, null, null,
                    escapeAndWrap(prompt), prompt, null,
                    null,
                    null, null, duration, "ERROR", e.getMessage());
            throw e;
        }
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages) {
        return delegate.generate(messages);
    }

    /**
     * Creates a minimal JSON escape for the prompt string.
     * Uses simple string concatenation for JSON wrapping (not an ObjectMapper for performance).
     */
    static String escapeAndWrap(String prompt) {
        return "{\"text\":" + escapeJson(prompt) + "}";
    }

    static String escapeJson(String s) {
        if (s == null) return "null";
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':  sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:   sb.append(c);
            }
        }
        sb.append("\"");
        return sb.toString();
    }
}
