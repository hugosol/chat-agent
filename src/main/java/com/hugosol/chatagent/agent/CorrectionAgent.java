package com.hugosol.chatagent.agent;

import com.hugosol.chatagent.agent.common.ErrorStrategy;
import com.hugosol.chatagent.agent.common.LlmReqConstructor;
import com.hugosol.chatagent.agent.common.LlmTaskDefinition;
import com.hugosol.chatagent.agent.common.TaskContext;
import com.hugosol.chatagent.agent.common.TaskName;
import com.hugosol.chatagent.config.PromptLoader;
import com.hugosol.chatagent.dto.CorrectionData;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Component
public class CorrectionAgent {

    private static final Logger log = LoggerFactory.getLogger(CorrectionAgent.class);

    private final LlmReqConstructor llmReqConstructor;
    private final ObjectMapper objectMapper;

    public CorrectionAgent(LlmReqConstructor llmReqConstructor, PromptLoader promptLoader, ObjectMapper objectMapper) {
        this.llmReqConstructor = llmReqConstructor;
        this.objectMapper = objectMapper;
        String systemTemplate = promptLoader.load("correction/system.txt").stripTrailing();
        llmReqConstructor.register(TaskName.CORRECTION, LlmTaskDefinition
                .<CorrectionParams, List<CorrectionData>>builder()
                .systemTemplate(systemTemplate)
                .userTemplate("User's utterance: {userInput}")
                .paramBuilder(p -> Map.of("userInput", p.userInput()))
                .parser(this::parseResponse)
                .errorStrategy(ErrorStrategy.SWALLOW)
                .build());
    }

    public List<CorrectionData> analyze(String userInput, TaskContext ctx) {
        if (userInput == null || userInput.isBlank()) {
            return Collections.emptyList();
        }

        List<CorrectionData> result = llmReqConstructor.execute(TaskName.CORRECTION,
                new CorrectionParams(userInput), ctx);
        return result != null ? result : Collections.emptyList();
    }

    private List<CorrectionData> parseResponse(String response) {
        try {
            String json = extractJson(response);
            if (json.isEmpty()) return Collections.emptyList();

            List<CorrectionData> corrections = objectMapper.readValue(
                    json, new TypeReference<List<CorrectionData>>() {});
            log.info("CorrectionAgent: parsed {} corrections", corrections.size());
            return corrections;
        } catch (Exception e) {
            log.warn("CorrectionAgent: failed to parse response, returning empty", e);
            return Collections.emptyList();
        }
    }

    private String extractJson(String response) {
        int start = response.indexOf('[');
        int end = response.lastIndexOf(']');
        if (start >= 0 && end > start) {
            return response.substring(start, end + 1);
        }
        return "";
    }

    private record CorrectionParams(String userInput) {}
}
