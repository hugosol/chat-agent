package com.hugosol.webagent.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hugosol.webagent.config.PromptLoader;
import com.hugosol.webagent.dto.MessageData;
import com.hugosol.webagent.model.AgentMode;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class MemoryCueAgent {

    private static final Logger log = LoggerFactory.getLogger(MemoryCueAgent.class);
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final Pattern JSON_ARRAY_PATTERN = Pattern.compile("\\[\\s*\\d+(?:\\s*,\\s*\\d+)*\\s*]");

    private final ChatLanguageModel chatModel;
    private final String splitTemplate;
    private final String entryTemplate;

    public MemoryCueAgent(ChatLanguageModel chatModel, PromptLoader promptLoader) {
        this.chatModel = chatModel;
        this.splitTemplate = promptLoader.load("memory-cue-split.txt");
        this.entryTemplate = promptLoader.load("memory-cue-entry.txt");
    }

    public record CueResult(String topic, String summary) {}

    public List<Integer> detectSwitches(List<MessageData> messages, AgentMode mode) {
        StringBuilder labeled = new StringBuilder();
        for (MessageData msg : messages) {
            labeled.append("[MSG#").append(msg.getMessageId()).append("] ")
                    .append(msg.getRole().name()).append(": ")
                    .append(msg.getContent()).append("\n");
        }

        String prompt = splitTemplate.replace("{messages}", labeled.toString());
        log.debug("MemoryCueAgent detectSwitches prompt length: {}", prompt.length());
        String response = chatModel.chat(prompt);
        log.debug("MemoryCueAgent detectSwitches response: {}", response);

        Matcher matcher = JSON_ARRAY_PATTERN.matcher(response);
        if (matcher.find()) {
            try {
                JsonNode array = mapper.readTree(matcher.group());
                List<Integer> result = new ArrayList<>();
                for (JsonNode node : array) {
                    result.add(node.asInt());
                }
                return result;
            } catch (Exception e) {
                log.warn("MemoryCueAgent: failed to parse switch array '{}': {}", response, e.getMessage());
                return Collections.emptyList();
            }
        }
        return Collections.emptyList();
    }

    public CueResult generateCue(List<MessageData> messages, AgentMode mode, int segmentIndex) {
        StringBuilder segment = new StringBuilder();
        for (MessageData msg : messages) {
            segment.append(msg.getRole().name()).append(": ")
                    .append(msg.getContent()).append("\n");
        }

        String prompt = entryTemplate.replace("{segment}", segment.toString());
        log.debug("MemoryCueAgent generateCue segment {} prompt length: {}", segmentIndex, prompt.length());
        String response = chatModel.chat(prompt);
        log.debug("MemoryCueAgent generateCue segment {} response: {}", segmentIndex, response);

        try {
            JsonNode node = mapper.readTree(response);
            String topic = node.has("topic") ? node.get("topic").asText() : "";
            String summary = node.has("summary") ? node.get("summary").asText() : "";
            return new CueResult(topic, summary);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse cue JSON: " + e.getMessage(), e);
        }
    }
}
