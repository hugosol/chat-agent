package com.hugosol.chatagent.agent;

import com.hugosol.chatagent.config.PromptLoader;
import com.hugosol.chatagent.dto.CueMatch;
import com.hugosol.chatagent.dto.MemoryContent;
import com.hugosol.chatagent.dto.MessageData;
import com.hugosol.chatagent.model.AgentMode;
import com.hugosol.chatagent.model.MessageRole;
import com.hugosol.chatagent.model.TimeLabel;
import com.hugosol.chatagent.service.UserPreferencesService;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
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
    private final UserPreferencesService userPreferencesService;
    private final String systemTemplate;
    private final Map<AgentMode, String> modeDescriptions = new EnumMap<>(AgentMode.class);
    private final Map<AgentMode, String> modeRules = new EnumMap<>(AgentMode.class);

    public ConversationAgent(StreamingChatLanguageModel chatModel, PromptLoader promptLoader,
                             UserPreferencesService userPreferencesService) {
        this.chatModel = chatModel;
        this.userPreferencesService = userPreferencesService;
        this.systemTemplate = promptLoader.load("conversation-system.txt");
        for (AgentMode mode : AgentMode.values()) {
            String path = mode.getTemplatePath();
            modeDescriptions.put(mode, promptLoader.load(path + "/description.txt"));
            modeRules.put(mode, promptLoader.load(path + "/rules.txt"));
        }
    }

    public void generateStream(List<MessageData> history, AgentMode mode,
                                MemoryContent memoryContent, String userId,
                                int messageId, StreamingChatResponseHandler handler) {
        generate(history, mode, memoryContent, userId, handler);
    }

    public String buildPromptJson(List<MessageData> history, AgentMode mode,
                                   MemoryContent memoryContent, String userId,
                                   int messageId) {
        List<ChatMessage> messages = buildMessages(history, mode, memoryContent, userId);
        return "[" + messages.stream()
                .map(m -> "{\"role\":\"" + roleName(m) + "\",\"content\":" + jsonEscape(contentOf(m)) + "}")
                .collect(Collectors.joining(",")) + "]";
    }

    private void generate(List<MessageData> history, AgentMode mode,
                           MemoryContent memoryContent, String userId,
                           StreamingChatResponseHandler handler) {
        List<ChatMessage> messages = buildMessages(history, mode, memoryContent, userId);

        log.debug("ConversationAgent sending {} messages", messages.size());
        chatModel.chat(messages, handler);
    }

    private List<ChatMessage> buildMessages(List<MessageData> history, AgentMode mode,
                                              MemoryContent memoryContent, String userId) {
        String systemContent = buildSystemContent(mode, memoryContent, userId);

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

    private String buildSystemContent(AgentMode mode, MemoryContent memoryContent, String userId) {
        String description = modeDescriptions.getOrDefault(mode, "");
        String rules = modeRules.getOrDefault(mode, "");

        String content = systemTemplate
                .replace("{Description}", description)
                .replace("{Rules}", rules);

        boolean hasLastConversation = memoryContent.lastConversationTimeLabel() != null;
        boolean hasLearningProfile = memoryContent.learningProfile() != null && !memoryContent.learningProfile().isBlank();
        boolean hasMemoryCues = memoryContent.cueMatches() != null && !memoryContent.cueMatches().isEmpty();
        boolean hasAnyMemory = hasLastConversation || hasLearningProfile || hasMemoryCues;

        if (hasLastConversation) {
            String label = memoryContent.lastConversationTimeLabel();
            content = content.replace("{lastConversation}",
                    "The last conversation was " + label + ". Pick up conversation naturally from where it left off.");
        } else {
            content = content.replace("{lastConversation}", "");
        }

        if (hasLearningProfile) {
            content = content.replace("{learningProfile}",
                    "[Your Learning Profile]\n" + memoryContent.learningProfile());
        } else {
            content = content.replace("{learningProfile}", "");
        }

        if (hasMemoryCues) {
            content = content.replace("{memoryCues}",
                    formatMemoryCuesForPrompt(memoryContent.cueMatches(), userId));
        } else {
            content = content.replace("{memoryCues}", "");
        }

        if (hasAnyMemory) {
            content = content.replace("{activeEngagement}", ACTIVE_ENGAGEMENT_TEXT);
        } else {
            content = content.replace("{activeEngagement}", "");
        }

        return content;
    }

    private String formatMemoryCuesForPrompt(List<CueMatch> cues, String userId) {
        StringBuilder sb = new StringBuilder("[Memory Cues]\n");
        java.time.Instant now = java.time.Instant.now();
        ZoneId zoneId = getZoneId(userId);
        for (int i = 0; i < cues.size(); i++) {
            CueMatch cue = cues.get(i);
            String timeLabel = TimeLabel.computeLabel(cue.createdAt(), now, zoneId);
            sb.append(i + 1).append(". [from ").append(timeLabel).append("] ")
                    .append(cue.topic()).append(": ").append(cue.summary()).append("\n");
        }
        return sb.toString();
    }

    private ZoneId getZoneId(String userId) {
        try {
            var prefs = userPreferencesService.get(userId);
            String tz = prefs.getTimezone();
            if (tz != null && !tz.isEmpty()) {
                return ZoneId.of(tz);
            }
        } catch (Exception ignored) {
        }
        return ZoneId.systemDefault();
    }
}
