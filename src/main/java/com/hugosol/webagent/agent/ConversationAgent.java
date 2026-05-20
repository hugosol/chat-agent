package com.hugosol.webagent.agent;

import com.hugosol.webagent.config.PromptLoader;
import com.hugosol.webagent.graph.MessageData;
import com.hugosol.webagent.model.PersonaType;
import com.hugosol.webagent.model.ScenarioType;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ConversationAgent {

    private static final Logger log = LoggerFactory.getLogger(ConversationAgent.class);
    private final StreamingChatLanguageModel chatModel;
    private final String promptTemplate;

    public ConversationAgent(StreamingChatLanguageModel chatModel, PromptLoader promptLoader) {
        this.chatModel = chatModel;
        this.promptTemplate = promptLoader.load("conversation.txt");
    }

    public void generateStream(String userInput, List<MessageData> history, String scenario, String persona,
                                StreamingChatResponseHandler handler) {
        PersonaType p = PersonaType.valueOf(persona);
        ScenarioType s = ScenarioType.valueOf(scenario);
        String historyText = buildHistoryText(history);

        String prompt = promptTemplate
                .replace("{persona_description}", p.getFullDescription())
                .replace("{persona_role}", p.getRoleDescription())
                .replace("{scenario}", s.getDescription())
                .replace("{history}", historyText)
                .replace("{userInput}", userInput);

        log.debug("ConversationAgent prompt length: {}", prompt.length());
        chatModel.chat(prompt, handler);
    }

    private String buildHistoryText(List<MessageData> history) {
        if (history.isEmpty()) return "(No previous messages)";
        StringBuilder sb = new StringBuilder();
        int start = Math.max(0, history.size() - 20);
        for (int i = start; i < history.size(); i++) {
            MessageData msg = history.get(i);
            sb.append(msg.getRole().name()).append(": ").append(msg.getContent()).append("\n");
        }
        return sb.toString();
    }
}
