package com.hugosol.chatagent.agent;

import com.hugosol.chatagent.agent.common.ErrorStrategy;
import com.hugosol.chatagent.agent.common.LlmReqConstructor;
import com.hugosol.chatagent.agent.common.LlmTaskDefinition;
import com.hugosol.chatagent.agent.common.TaskContext;
import com.hugosol.chatagent.agent.common.TaskName;
import com.hugosol.chatagent.config.AppProperties;
import com.hugosol.chatagent.config.PromptLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class LearningAgent {

    private static final Logger log = LoggerFactory.getLogger(LearningAgent.class);
    private static final String USER_DELIMITER = "---USER---";

    private final LlmReqConstructor llmReqConstructor;

    public LearningAgent(LlmReqConstructor llmReqConstructor, PromptLoader promptLoader, AppProperties appProperties) {
        this.llmReqConstructor = llmReqConstructor;
        String fullTemplate = promptLoader.load("memory-profile.txt");
        String[] parts = fullTemplate.split(USER_DELIMITER, 2);
        String systemTemplate = parts[0].stripTrailing()
                .replace("{profileMaxLength}", String.valueOf(appProperties.getMemory().getProfileMaxLength()));
        String userTemplate = parts.length > 1 ? parts[1].strip() : "{oldLearningProfile}\n{errorSummary}";
        llmReqConstructor.register(TaskName.MERGE_LEARNING, LlmTaskDefinition
                .<MergeLearningParams, String>builder()
                .systemTemplate(systemTemplate)
                .userTemplate(userTemplate)
                .paramBuilder(p -> Map.of(
                        "oldLearningProfile", p.oldProfile().isEmpty() ? "(No previous sessions)" : p.oldProfile(),
                        "errorSummary", p.errorSummary()
                ))
                .parser(String::trim)
                .errorStrategy(ErrorStrategy.SWALLOW)
                .build());
    }

    public String mergeProfile(String oldProfile, String errorSummary, TaskContext ctx) {
        log.debug("LearningAgent mergeProfile...");
        String result = llmReqConstructor.execute(TaskName.MERGE_LEARNING,
                new MergeLearningParams(oldProfile, errorSummary), ctx);
        return result != null ? result : "";
    }

    private record MergeLearningParams(String oldProfile, String errorSummary) {}
}
