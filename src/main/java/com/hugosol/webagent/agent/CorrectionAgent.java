package com.hugosol.webagent.agent;

import com.hugosol.webagent.config.PromptLoader;
import com.hugosol.webagent.graph.CorrectionData;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
public class CorrectionAgent {

    private static final Logger log = LoggerFactory.getLogger(CorrectionAgent.class);
    private final ChatLanguageModel chatModel;
    private final String promptTemplate;
    private final ObjectMapper objectMapper;

    public CorrectionAgent(ChatLanguageModel chatModel, PromptLoader promptLoader, ObjectMapper objectMapper) {
        this.chatModel = chatModel;
        this.promptTemplate = promptLoader.load("correction.txt");
        this.objectMapper = objectMapper;
    }

    public List<CorrectionData> analyze(String userInput) {
        if (userInput == null || userInput.isBlank()) {
            return Collections.emptyList();
        }

        String prompt = promptTemplate.replace("{userInput}", userInput);

        log.debug("CorrectionAgent prompt length: {}", prompt.length());
        String response = chatModel.chat(prompt);
        log.debug("CorrectionAgent raw response: {}", response);

        return parseResponse(response);
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
}
