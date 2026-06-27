package com.hugosol.chatagent.agent;

import com.hugosol.chatagent.agent.common.ErrorStrategy;
import com.hugosol.chatagent.agent.common.LlmReqConstructor;
import com.hugosol.chatagent.agent.common.LlmTaskDefinition;
import com.hugosol.chatagent.agent.common.TaskContext;
import com.hugosol.chatagent.agent.common.TaskName;
import com.hugosol.chatagent.config.PromptLoader;
import com.hugosol.chatagent.dto.CorrectionData;
import com.hugosol.chatagent.dto.MessageData;
import com.hugosol.chatagent.model.MessageRole;
import com.hugosol.chatagent.model.AgentMode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class ReportAgent {

    private static final Logger log = LoggerFactory.getLogger(ReportAgent.class);
    private static final String USER_DELIMITER = "---USER---";

    private final LlmReqConstructor llmReqConstructor;
    private final ObjectMapper objectMapper;

    private final Map<AgentMode, String> reportSystemTemplates = new EnumMap<>(AgentMode.class);

    public ReportAgent(LlmReqConstructor llmReqConstructor, PromptLoader promptLoader, ObjectMapper objectMapper) {
        this.llmReqConstructor = llmReqConstructor;
        this.objectMapper = objectMapper;

        String[] rootParts = promptLoader.load("report.txt").split(USER_DELIMITER, 2);
        String rootSystemTemplate = rootParts[0].stripTrailing();
        String rootUserTemplate = rootParts.length > 1 ? rootParts[1].strip() : "{fullConversation}\n{allCorrections}";

        llmReqConstructor.register(TaskName.REPORT, LlmTaskDefinition
                .<ReportParams, ReportResult>builder()
                .systemTemplate(rootSystemTemplate)
                .userTemplate(rootUserTemplate)
                .paramBuilder(p -> Map.of(
                        "fullConversation", buildConversationText(p.messages()),
                        "allCorrections", buildErrorsText(p.corrections())
                ))
                .parser(this::parseReport)
                .errorStrategy(ErrorStrategy.SWALLOW)
                .build());

        // Load per-mode system template overrides
        for (AgentMode mode : AgentMode.values()) {
            String path = mode.getTemplatePath();
            String perModeFull = promptLoader.loadIfExists(path + "/report.txt", null);
            if (perModeFull != null) {
                String[] modeParts = perModeFull.split(USER_DELIMITER, 2);
                reportSystemTemplates.put(mode, modeParts[0].stripTrailing());
            }
        }
    }

    public ReportResult generate(List<MessageData> messages, List<CorrectionData> allCorrections, TaskContext ctx) {
        log.debug("ReportAgent generating...");
        AgentMode mode;
        try {
            mode = AgentMode.valueOf(ctx.mode());
        } catch (IllegalArgumentException e) {
            mode = AgentMode.WORKPLACE_STANDUP;
        }
        String systemOverride = reportSystemTemplates.get(mode);
        ReportResult result;
        if (systemOverride != null) {
            result = llmReqConstructor.execute(TaskName.REPORT,
                    new ReportParams(messages, allCorrections), ctx, systemOverride);
        } else {
            result = llmReqConstructor.execute(TaskName.REPORT,
                    new ReportParams(messages, allCorrections), ctx);
        }
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
