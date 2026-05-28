package com.hugosol.webagent.agent;

import com.hugosol.webagent.config.PromptLoader;
import com.hugosol.webagent.dto.CorrectionData;
import com.hugosol.webagent.dto.MessageData;
import com.hugosol.webagent.model.MessageRole;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class ReportAgent {

    private static final Logger log = LoggerFactory.getLogger(ReportAgent.class);
    private final ChatLanguageModel chatModel;
    private final String promptTemplate;
    private final ObjectMapper objectMapper;

    public ReportAgent(ChatLanguageModel chatModel, PromptLoader promptLoader, ObjectMapper objectMapper) {
        this.chatModel = chatModel;
        this.promptTemplate = promptLoader.load("report.txt");
        this.objectMapper = objectMapper;
    }

    public ReportResult generate(List<MessageData> messages, List<CorrectionData> allCorrections) {
        String conversationText = buildConversationText(messages);
        String errorsText = buildErrorsText(allCorrections);

        String prompt = promptTemplate
                .replace("{fullConversation}", conversationText)
                .replace("{allCorrections}", errorsText);

        log.debug("ReportAgent INPUT:\n{}", prompt);
        String response = chatModel.chat(prompt);
        log.debug("ReportAgent OUTPUT:\n{}", response);

        return parseReport(response);
    }

    private ReportResult parseReport(String response) {
        try {
            Map<String, Object> sections = objectMapper
                    .readerFor(Map.class)
                    .with(com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_UNQUOTED_CONTROL_CHARS)
                    .readValue(response);
            return new ReportResult(
                    getString(sections, "overallAssessment"),
                    getString(sections, "topicSummary"),
                    getString(sections, "errorSummary"),
                    getInt(sections, "fluencyScore"),
                    getString(sections, "keyTakeaway")
            );
        } catch (Exception e) {
            log.warn("ReportAgent: failed to parse JSON, raw response:\n{}", response, e);
            return new ReportResult(response, "", "", 0, "");
        }
    }

    private String getString(Map<String, Object> map, String key) {
        Object value = map.getOrDefault(key, "");
        if (value instanceof String s) return s;
        if (value instanceof Map || value instanceof List) {
            try { return objectMapper.writeValueAsString(value); } catch (Exception e) { return ""; }
        }
        return value != null ? value.toString() : "";
    }

    private int getInt(Map<String, Object> map, String key) {
        Object value = map.getOrDefault(key, 0);
        if (value instanceof Number n) return n.intValue();
        if (value instanceof String s) {
            try { return Integer.parseInt(s); } catch (NumberFormatException e) { return 0; }
        }
        return 0;
    }

    private String buildConversationText(List<MessageData> messages) {
        StringBuilder sb = new StringBuilder();
        for (MessageData msg : messages) {
            if (msg.getRole() != MessageRole.CORRECTION) {
                sb.append(msg.getRole()).append(": ").append(msg.getContent()).append("\n");
            }
        }
        return sb.toString();
    }

    private String buildErrorsText(List<CorrectionData> corrections) {
        if (corrections.isEmpty()) return "No errors recorded.";
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(corrections);
        } catch (Exception e) {
            return corrections.toString();
        }
    }

    public record ReportResult(
            String overallAssessment,
            String topicSummary,
            String errorSummary,
            int fluencyScore,
            String keyTakeaway
    ) {}
}
