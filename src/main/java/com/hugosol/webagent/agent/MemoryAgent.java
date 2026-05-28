package com.hugosol.webagent.agent;

import com.hugosol.webagent.config.AppProperties;
import com.hugosol.webagent.config.PromptLoader;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class MemoryAgent {

    private static final Logger log = LoggerFactory.getLogger(MemoryAgent.class);
    private final ChatLanguageModel chatModel;
    private final String profileTemplate;

    public MemoryAgent(ChatLanguageModel chatModel, PromptLoader promptLoader, AppProperties appProperties) {
        this.chatModel = chatModel;
        String raw = promptLoader.load("memory-profile.txt");
        this.profileTemplate = raw.replace("{profileMaxLength}", String.valueOf(appProperties.getMemory().getProfileMaxLength()));
    }

    public String mergeProfile(String oldProfile, String errorSummary) {
        String prompt = profileTemplate
                .replace("{oldLearningProfile}", oldProfile.isEmpty() ? "(No previous sessions)" : oldProfile)
                .replace("{errorSummary}", errorSummary);

        log.debug("MemoryAgent mergeProfile INPUT:\n{}", prompt);
        String response = chatModel.chat(prompt);
        log.debug("MemoryAgent mergeProfile OUTPUT:\n{}", response);
        return response.trim();
    }
}
