package com.hugosol.webagent.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hugosol.webagent.config.AppProperties;
import com.hugosol.webagent.config.PromptLoader;
import com.hugosol.webagent.dto.MessageData;
import com.hugosol.webagent.model.AgentMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class MemoryCueAgent {

    private static final Logger log = LoggerFactory.getLogger(MemoryCueAgent.class);
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final Pattern JSON_ARRAY_PATTERN = Pattern.compile("\\[\\s*\\d+(?:\\s*,\\s*\\d+)*\\s*]");

    private final TaskRunner runner;

    public MemoryCueAgent(TaskRunner runner, PromptLoader promptLoader, AppProperties appProperties) {
        this.runner = runner;

        String splitTemplate = promptLoader.load("memory-cue-split.txt");
        runner.register(TaskName.CHAT_SWITCHES, TaskDefinition
                .<SwitchParams, List<Integer>>builder()
                .template(splitTemplate)
                .paramBuilder(p -> Map.of("messages", buildLabeledMessages(p.messages())))
                .parser(this::parseSwitches)
                .errorStrategy(ErrorStrategy.SWALLOW)
                .build());

        String raw = promptLoader.load("memory-cue-entry.txt");
        String entryTemplate = raw
                .replace("{cueTopicMaxWords}", String.valueOf(appProperties.getMemory().getCueTopicMaxWords()))
                .replace("{cueSummaryMaxSentences}", String.valueOf(appProperties.getMemory().getCueSummaryMaxSentences()));
        runner.register(TaskName.GENERATE_MEMORY_CUE, TaskDefinition
                .<CueParams, CueResult>builder()
                .template(entryTemplate)
                .paramBuilder(p -> Map.of("segment", buildSegmentText(p.messages())))
                .parser(this::parseCue)
                .errorStrategy(ErrorStrategy.SWALLOW)
                .build());
    }

    public record CueResult(String topic, String summary) {}

    public List<Integer> detectSwitches(List<MessageData> messages, AgentMode mode, TaskContext ctx) {
        log.debug("MemoryCueAgent detectSwitches...");
        List<Integer> result = runner.execute(TaskName.CHAT_SWITCHES,
                new SwitchParams(messages), ctx);
        return result != null ? result : Collections.emptyList();
    }

    public CueResult generateCue(List<MessageData> messages, AgentMode mode, int segmentIndex, TaskContext ctx) {
        log.debug("MemoryCueAgent generateCue segment {}...", segmentIndex);
        CueResult result = runner.execute(TaskName.GENERATE_MEMORY_CUE,
                new CueParams(messages, segmentIndex), ctx);
        if (result == null) {
            throw new RuntimeException("Failed to parse cue JSON: SWALLOW returned null");
        }
        return result;
    }

    private String buildLabeledMessages(List<MessageData> messages) {
        StringBuilder labeled = new StringBuilder();
        for (MessageData msg : messages) {
            labeled.append("[MSG#").append(msg.getMessageId()).append("] ")
                    .append(msg.getRole().name()).append(": ")
                    .append(msg.getContent()).append("\n");
        }
        return labeled.toString();
    }

    private String buildSegmentText(List<MessageData> messages) {
        StringBuilder segment = new StringBuilder();
        for (MessageData msg : messages) {
            segment.append(msg.getRole().name()).append(": ")
                    .append(msg.getContent()).append("\n");
        }
        return segment.toString();
    }

    private List<Integer> parseSwitches(String response) {
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

    private CueResult parseCue(String response) {
        try {
            JsonNode node = mapper.readTree(response);
            String topic = node.has("topic") ? node.get("topic").asText() : "";
            String summary = node.has("summary") ? node.get("summary").asText() : "";
            return new CueResult(topic, summary);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse cue JSON: " + e.getMessage(), e);
        }
    }

    private record SwitchParams(List<MessageData> messages) {}
    private record CueParams(List<MessageData> messages, int segmentIndex) {}
}
