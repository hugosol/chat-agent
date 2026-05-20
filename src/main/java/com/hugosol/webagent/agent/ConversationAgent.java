package com.hugosol.webagent.agent;

import com.hugosol.webagent.config.PromptLoader;
import com.hugosol.webagent.graph.MessageData;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ConversationAgent {

    private static final Logger log = LoggerFactory.getLogger(ConversationAgent.class);
    private final ChatLanguageModel chatModel;
    private final String promptTemplate;

    public ConversationAgent(ChatLanguageModel chatModel, PromptLoader promptLoader) {
        this.chatModel = chatModel;
        this.promptTemplate = promptLoader.load("conversation.txt");
    }

    public String generate(String userInput, List<MessageData> history, String scenario, String persona) {
        String personaDesc = buildPersonaDescription(persona);
        String historyText = buildHistoryText(history);

        String prompt = promptTemplate
                .replace("{persona_description}", personaDesc)
                .replace("{scenario_role}", persona)
                .replace("{scenario}", scenario)
                .replace("{history}", historyText)
                .replace("{userInput}", userInput);

        log.debug("ConversationAgent prompt length: {}", prompt.length());
        String response = chatModel.chat(prompt);
        log.debug("ConversationAgent response length: {}", response.length());
        return response;
    }

    private String buildPersonaDescription(String persona) {
        return switch (persona) {
            case "TEAM_COLLEAGUE" -> "You are a friendly teammate at a software company. "
                    + "You discuss daily work, projects, and tech topics casually.";
            case "MANAGER" -> "You are a supportive engineering manager having a 1-on-1 meeting. "
                    + "You ask about progress, blockers, and career growth.";
            default -> "You are a friendly English-speaking colleague. Keep conversations natural and engaging.";
        };
    }

    private String buildHistoryText(List<MessageData> history) {
        if (history.isEmpty()) return "(No previous messages)";
        StringBuilder sb = new StringBuilder();
        int start = Math.max(0, history.size() - 20);
        for (int i = start; i < history.size(); i++) {
            MessageData msg = history.get(i);
            sb.append(msg.getRole()).append(": ").append(msg.getContent()).append("\n");
        }
        return sb.toString();
    }
}
