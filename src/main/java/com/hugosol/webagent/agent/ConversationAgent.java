package com.hugosol.webagent.agent;

import com.hugosol.webagent.config.PromptLoader;
import com.hugosol.webagent.dto.MessageData;
import com.hugosol.webagent.model.MessageRole;
import com.hugosol.webagent.model.PersonaType;
import com.hugosol.webagent.model.ScenarioType;
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
import java.util.List;

@Component
public class ConversationAgent {

    private static final Logger log = LoggerFactory.getLogger(ConversationAgent.class);
    private static final String ACTIVE_ENGAGEMENT_TEXT =
            "[Active Engagement]\n" +
            "Based on the memory above, if there is an unfinished topic, naturally bring it up early " +
            "in the conversation. Ask the user questions about their interests to keep the conversation engaging.";

    private final StreamingChatLanguageModel chatModel;
    private final String systemTemplate;

    public ConversationAgent(StreamingChatLanguageModel chatModel, PromptLoader promptLoader) {
        this.chatModel = chatModel;
        this.systemTemplate = promptLoader.load("conversation-system.txt");
    }

    public void generateStream(List<MessageData> history, String scenario, String persona,
                                StreamingChatResponseHandler handler) {
        generate(history, scenario, persona, null, null, false, handler);
    }

    public void generateStreamFirstTurn(List<MessageData> history, String scenario, String persona,
                                         String topicSummary, String learningProfile,
                                         StreamingChatResponseHandler handler) {
        generate(history, scenario, persona, topicSummary, learningProfile, true, handler);
    }

    private void generate(List<MessageData> history, String scenario, String persona,
                          String topicSummary, String learningProfile, boolean injectMemory,
                          StreamingChatResponseHandler handler) {
        PersonaType p = PersonaType.valueOf(persona);
        ScenarioType s = ScenarioType.valueOf(scenario);

        String systemContent = buildSystemContent(p, s, topicSummary, learningProfile, injectMemory);

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(SystemMessage.from(systemContent));

        int start = Math.max(0, history.size() - 20);
        for (int i = start; i < history.size(); i++) {
            MessageData msg = history.get(i);
            if (msg.getRole() == MessageRole.USER) {
                messages.add(UserMessage.from(msg.getContent()));
            } else if (msg.getRole() == MessageRole.AGENT) {
                messages.add(AiMessage.from(msg.getContent()));
            }
        }

        log.debug("ConversationAgent sending {} messages", messages.size());
        chatModel.chat(messages, handler);
    }

    private String buildSystemContent(PersonaType p, ScenarioType s,
                                       String topicSummary, String learningProfile, boolean injectMemory) {
        String content = systemTemplate
                .replace("{persona_description}", p.getFullDescription())
                .replace("{persona_role}", p.getRoleDescription())
                .replace("{scenario}", s.getDescription());

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
