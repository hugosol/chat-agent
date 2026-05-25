package com.hugosol.webagent.agent;

import com.hugosol.webagent.config.PromptLoader;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class MemoryAgent {

    private static final Logger log = LoggerFactory.getLogger(MemoryAgent.class);
    private final ChatLanguageModel chatModel;
    private final String topicTemplate;
    private final String profileTemplate;

    public MemoryAgent(ChatLanguageModel chatModel, PromptLoader promptLoader) {
        this.chatModel = chatModel;
        this.topicTemplate = promptLoader.load("memory-topic.txt");
        this.profileTemplate = promptLoader.load("memory-profile.txt");
    }

    public String mergeTopic(String oldSummary, String newSessionSummary) {
        String prompt = topicTemplate
                .replace("{oldTopicSummary}", oldSummary.isEmpty() ? "(No previous sessions)" : oldSummary)
                .replace("{newSessionSummary}", newSessionSummary);

        log.debug("MemoryAgent mergeTopic INPUT:\n{}", prompt);
        String response = chatModel.chat(prompt);
        log.debug("MemoryAgent mergeTopic OUTPUT:\n{}", response);
        return response.trim();
    }

    public String mergeProfile(String oldProfile, String errorSummary, String vocabularySuggestions) {
        String prompt = profileTemplate
                .replace("{oldLearningProfile}", oldProfile.isEmpty() ? "(No previous sessions)" : oldProfile)
                .replace("{errorSummary}", errorSummary)
                .replace("{vocabularySuggestions}", vocabularySuggestions);

        log.debug("MemoryAgent mergeProfile INPUT:\n{}", prompt);
        String response = chatModel.chat(prompt);
        log.debug("MemoryAgent mergeProfile OUTPUT:\n{}", response);
        return response.trim();
    }
}
