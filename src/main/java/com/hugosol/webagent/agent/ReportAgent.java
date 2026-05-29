package com.hugosol.webagent.agent;

import com.hugosol.webagent.agent.common.ErrorStrategy;
import com.hugosol.webagent.agent.common.TaskContext;
import com.hugosol.webagent.agent.common.TaskDefinition;
import com.hugosol.webagent.agent.common.TaskName;
import com.hugosol.webagent.agent.common.TaskRunner;
import com.hugosol.webagent.config.PromptLoader;
import com.hugosol.webagent.dto.CorrectionData;
import com.hugosol.webagent.dto.MessageData;
import com.hugosol.webagent.model.MessageRole;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class ReportAgent {

    private static final Logger log = LoggerFactory.getLogger(ReportAgent.class);
    private final TaskRunner runner;
    private final ObjectMapper objectMapper;

    public ReportAgent(TaskRunner runner, PromptLoader promptLoader, ObjectMapper objectMapper) {
        this.runner = runner;
        this.objectMapper = objectMapper;
        String template = promptLoader.load("report.txt");
        runner.register(TaskName.REPORT, TaskDefinition
                .<ReportParams, ReportResult>builder()
                .template(template)
                .paramBuilder(p -> Map.of(
                        "fullConversation", buildConversationText(p.messages()),
                        "allCorrections", buildErrorsText(p.corrections())
                ))
                .parser(this::parseReport)
                .errorStrategy(ErrorStrategy.SWALLOW)
                .build());
    }

    public ReportResult generate(List<MessageData> messages, List<CorrectionData> allCorrections, TaskContext ctx) {
        log.debug("ReportAgent generating...");
        ReportResult result = runner.requestModel(TaskName.REPORT,
                new ReportParams(messages, allCorrections), ctx);
        return result != null ? result : new ReportResult("", "", 0, "");
    }

    private ReportResult parseReport(String response) {
        try {
            Map<String, Object> sections = objectMapper
                    .readerFor(Map.class)
                    .with(com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_UNQUOTED_CONTROL_CHARS)
                    .readValue(response);
            return new ReportResult(
                    getString(sections, "overallAssessment"),
                    getString(sections, "errorSummary"),
                    getInt(sections, "fluencyScore"),
                    getString(sections, "keyTakeaway")
            );
        } catch (Exception e) {
            log.warn("ReportAgent: failed to parse JSON, raw response:\n{}", response, e);
            return new ReportResult(response, "", 0, "");
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
            String errorSummary,
            int fluencyScore,
            String keyTakeaway
    ) {}

    private record ReportParams(List<MessageData> messages, List<CorrectionData> corrections) {}
}
