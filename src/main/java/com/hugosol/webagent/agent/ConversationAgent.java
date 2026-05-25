package com.hugosol.webagent.agent;

import com.hugosol.webagent.config.PromptLoader;
import com.hugosol.webagent.dto.MessageData;
import com.hugosol.webagent.model.AgentMode;
import com.hugosol.webagent.model.MessageRole;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

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
                                String topicSummary, String learningProfile,
                                int messageId, StreamingChatResponseHandler handler) {
        boolean hasMemory = (topicSummary != null && !topicSummary.isBlank())
                || (learningProfile != null && !learningProfile.isBlank());
        boolean injectMemory = messageId <= 3 && hasMemory;
        generate(history, mode, topicSummary, learningProfile, injectMemory, handler);
    }

    public String buildPromptJson(List<MessageData> history, AgentMode mode,
                                   String topicSummary, String learningProfile,
                                   int messageId) {
        boolean hasMemory = (topicSummary != null && !topicSummary.isBlank())
                || (learningProfile != null && !learningProfile.isBlank());
        boolean injectMemory = messageId <= 3 && hasMemory;
        List<ChatMessage> messages = buildMessages(history, mode, topicSummary, learningProfile, injectMemory);
        return "[" + messages.stream()
                .map(m -> "{\"role\":\"" + roleName(m) + "\",\"content\":" + jsonEscape(contentOf(m)) + "}")
                .collect(Collectors.joining(",")) + "]";
    }

    private void generate(List<MessageData> history, AgentMode mode,
                          String topicSummary, String learningProfile, boolean injectMemory,
                          StreamingChatResponseHandler handler) {
        List<ChatMessage> messages = buildMessages(history, mode, topicSummary, learningProfile, injectMemory);

        log.debug("ConversationAgent sending {} messages", messages.size());
        chatModel.chat(messages, handler);
    }

    private List<ChatMessage> buildMessages(List<MessageData> history, AgentMode mode,
                                            String topicSummary, String learningProfile, boolean injectMemory) {
        String systemContent = buildSystemContent(mode, topicSummary, learningProfile, injectMemory);

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

    private String buildSystemContent(AgentMode mode,
                                       String topicSummary, String learningProfile, boolean injectMemory) {
        String description = modeDescriptions.getOrDefault(mode, "");
        String rules = modeRules.getOrDefault(mode, "");

        String content = systemTemplate
                .replace("{Description}", description)
                .replace("{Rules}", rules);

        if (injectMemory) {
            content = content
                    .replace("{topicSummary}", topicSummary.isEmpty() ? "" : "[Conversation Memory]\n" + topicSummary)
                    .replace("{learningProfile}", learningProfile.isEmpty() ? "" : "[Your Learning Profile]\n" + learningProfile)
                    .replace("{activeEngagement}", ACTIVE_ENGAGEMENT_TEXT);
        } else {
            content = content
                    .replace("{topicSummary}", "")
                    .replace("{learningProfile}", "")
                    .replace("{activeEngagement}", "");
        }
        return content;
    }
}
