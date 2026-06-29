package com.hugosol.chatagent.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hugosol.chatagent.agent.common.ErrorStrategy;
import com.hugosol.chatagent.agent.common.ExampleMsgFormatter;
import com.hugosol.chatagent.agent.common.LlmReqConstructor;
import com.hugosol.chatagent.agent.common.LlmTaskDefinition;
import com.hugosol.chatagent.agent.common.TaskContext;
import com.hugosol.chatagent.agent.common.TaskName;
import com.hugosol.chatagent.config.AppProperties;
import com.hugosol.chatagent.config.PromptLoader;
import com.hugosol.chatagent.dto.MessageData;
import com.hugosol.chatagent.model.AgentMode;
import com.hugosol.chatagent.model.MessageRole;
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

    private final LlmReqConstructor llmReqConstructor;

    public MemoryCueAgent(LlmReqConstructor llmReqConstructor, PromptLoader promptLoader, AppProperties appProperties) {
        this.llmReqConstructor = llmReqConstructor;

        String splitSystemTemplate = promptLoader.load("memory-cue/split/system.txt").stripTrailing()
                .replace("{cueTopicMaxWords}", String.valueOf(appProperties.getMemory().getCueTopicMaxWords()))
                .replace("{cueSummaryMaxSentences}", String.valueOf(appProperties.getMemory().getCueSummaryMaxSentences()));
        llmReqConstructor.register(TaskName.CHAT_SWITCHES, LlmTaskDefinition
                .<SwitchParams, List<Integer>>builder()
                .systemTemplate(splitSystemTemplate)
                .userTemplate("{messages}")
                .paramBuilder(p -> Map.of("messages", ExampleMsgFormatter.toXml(p.messages())))
                .parser(this::parseSwitches)
                .errorStrategy(ErrorStrategy.SWALLOW)
                .build());

        String entrySystemTemplate = promptLoader.load("memory-cue/entry/system.txt").stripTrailing()
                .replace("{cueTopicMaxWords}", String.valueOf(appProperties.getMemory().getCueTopicMaxWords()))
                .replace("{cueSummaryMaxSentences}", String.valueOf(appProperties.getMemory().getCueSummaryMaxSentences()));
        llmReqConstructor.register(TaskName.GENERATE_MEMORY_CUE, LlmTaskDefinition
                .<CueParams, CueResult>builder()
                .systemTemplate(entrySystemTemplate)
                .userTemplate("{segment}")
                .paramBuilder(p -> Map.of("segment", ExampleMsgFormatter.toXml(p.messages())))
                .parser(this::parseCue)
                .errorStrategy(ErrorStrategy.SWALLOW)
                .build());
    }

    public record CueResult(String topic, String summary) {}

    public List<Integer> detectSwitches(List<MessageData> messages, AgentMode mode, TaskContext ctx) {
        log.debug("MemoryCueAgent detectSwitches...");
        List<Integer> result = llmReqConstructor.execute(TaskName.CHAT_SWITCHES,
                new SwitchParams(messages), ctx);
        return result != null ? result : Collections.emptyList();
    }

    public CueResult generateCue(List<MessageData> messages, AgentMode mode, int segmentIndex, TaskContext ctx) {
        log.debug("MemoryCueAgent generateCue segment {}...", segmentIndex);
        CueResult result = llmReqConstructor.execute(TaskName.GENERATE_MEMORY_CUE,
                new CueParams(messages, segmentIndex), ctx);
        if (result == null) {
            throw new RuntimeException("Failed to parse cue JSON: SWALLOW returned null");
        }
        return result;
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
