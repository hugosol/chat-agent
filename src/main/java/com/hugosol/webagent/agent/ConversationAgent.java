package com.hugosol.webagent.agent;

import com.hugosol.webagent.config.PromptLoader;
import com.hugosol.webagent.dto.CueMatch;
import com.hugosol.webagent.dto.MemoryContent;
import com.hugosol.webagent.dto.MessageData;
import com.hugosol.webagent.model.AgentMode;
import com.hugosol.webagent.model.MessageRole;
import com.hugosol.webagent.model.TimeLabel;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class ConversationAgent {

    private static final Logger log = LoggerFactory.getLogger(ConversationAgent.class);
    private static final String ACTIVE_ENGAGEMENT_TEXT =
            "[Active Engagement]\n" +
            "Based on the memory above, if there is an unfinished topic, naturally bring it up early " +
            "in the conversation. Ask the user questions about their interests to keep the conversation engaging.";

    private final StreamingChatLanguageModel chatModel;
    private final String systemTemplate;
    private final Map<AgentMode, String> modeDescriptions = new EnumMap<>(AgentMode.class);
    private final Map<AgentMode, String> modeRules = new EnumMap<>(AgentMode.class);

    public ConversationAgent(StreamingChatLanguageModel chatModel, PromptLoader promptLoader) {
        this.chatModel = chatModel;
        this.systemTemplate = promptLoader.load("conversation-system.txt");
        for (AgentMode mode : AgentMode.values()) {
            String path = mode.getTemplatePath();
            modeDescriptions.put(mode, promptLoader.load(path + "/description.txt"));
            modeRules.put(mode, promptLoader.load(path + "/rules.txt"));
        }
    }

    public void generateStream(List<MessageData> history, AgentMode mode,
                                MemoryContent memoryContent,
                                int messageId, StreamingChatResponseHandler handler) {
        generate(history, mode, memoryContent, handler);
    }

    public String buildPromptJson(List<MessageData> history, AgentMode mode,
                                   MemoryContent memoryContent,
                                   int messageId) {
        List<ChatMessage> messages = buildMessages(history, mode, memoryContent);
        return "[" + messages.stream()
                .map(m -> "{\"role\":\"" + roleName(m) + "\",\"content\":" + jsonEscape(contentOf(m)) + "}")
                .collect(Collectors.joining(",")) + "]";
    }

    private void generate(List<MessageData> history, AgentMode mode,
                           MemoryContent memoryContent,
                           StreamingChatResponseHandler handler) {
        List<ChatMessage> messages = buildMessages(history, mode, memoryContent);

        log.debug("ConversationAgent sending {} messages", messages.size());
        chatModel.chat(messages, handler);
    }

    private List<ChatMessage> buildMessages(List<MessageData> history, AgentMode mode,
                                             MemoryContent memoryContent) {
        String systemContent = buildSystemContent(mode, memoryContent);

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(SystemMessage.from(systemContent));

        for (int i = 0; i < history.size(); i++) {
            MessageData msg = history.get(i);
            if (msg.getRole() == MessageRole.USER) {
                messages.add(UserMessage.from(msg.getContent()));
            } else if (msg.getRole() == MessageRole.AGENT) {
                messages.add(AiMessage.from(msg.getContent()));
            }
        }
        return messages;
    }

    private static String roleName(ChatMessage msg) {
        if (msg instanceof SystemMessage) return "system";
        if (msg instanceof AiMessage) return "assistant";
        return "user";
    }

    private static String contentOf(ChatMessage msg) {
        String text = msg instanceof SystemMessage sm ? sm.text()
                : msg instanceof UserMessage um ? um.singleText()
                : msg instanceof AiMessage am ? am.text()
                : msg.toString();
        return text != null ? text : "";
    }

    private static String jsonEscape(String s) {
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

    private String buildSystemContent(AgentMode mode, MemoryContent memoryContent) {
        String description = modeDescriptions.getOrDefault(mode, "");
        String rules = modeRules.getOrDefault(mode, "");

        String content = systemTemplate
                .replace("{Description}", description)
                .replace("{Rules}", rules);

        boolean hasUserMemory = memoryContent.topicSummary() != null && !memoryContent.topicSummary().isBlank()
                || memoryContent.learningProfile() != null && !memoryContent.learningProfile().isBlank();
        boolean hasRagMemory = memoryContent.cueMatches() != null && !memoryContent.cueMatches().isEmpty();

        if (hasUserMemory) {
            String ts = memoryContent.topicSummary();
            String lp = memoryContent.learningProfile();
            String tsFormatted = "";
            if (ts != null && !ts.isBlank()) {
                String timePrefix = buildTimePrefix(memoryContent.topicCreatedAt());
                tsFormatted = "[Conversation Memory]\n" + timePrefix + ts;
            }
            content = content
                    .replace("{topicSummary}", tsFormatted)
                    .replace("{memoryCues}", "")
                    .replace("{learningProfile}", lp != null && !lp.isBlank()
                            ? "[Your Learning Profile]\n" + lp : "")
                    .replace("{activeEngagement}", ACTIVE_ENGAGEMENT_TEXT);
        } else if (hasRagMemory) {
            String cuesText = formatMemoryCuesForPrompt(memoryContent.cueMatches());
            content = content
                    .replace("{topicSummary}", "")
                    .replace("{memoryCues}", cuesText)
                    .replace("{learningProfile}", "")
                    .replace("{activeEngagement}", ACTIVE_ENGAGEMENT_TEXT);
        } else {
            content = content
                    .replace("{topicSummary}", "")
                    .replace("{memoryCues}", "")
                    .replace("{learningProfile}", "")
                    .replace("{activeEngagement}", "");
        }
        return content;
    }

    private static String buildTimePrefix(LocalDateTime eventTime) {
        if (eventTime == null) return "";
        String label = TimeLabel.computeLabel(eventTime, LocalDateTime.now());
        return "[from " + label + "] ";
    }

    private static String formatMemoryCuesForPrompt(List<CueMatch> cues) {
        StringBuilder sb = new StringBuilder("[Memory Cues]\n");
        LocalDateTime now = LocalDateTime.now();
        for (int i = 0; i < cues.size(); i++) {
            CueMatch cue = cues.get(i);
            String timeLabel = TimeLabel.computeLabel(cue.createdAt(), now);
            sb.append(i + 1).append(". [from ").append(timeLabel).append("] ")
                    .append(cue.topic()).append(": ").append(cue.summary()).append("\n");
        }
        return sb.toString();
    }
}
