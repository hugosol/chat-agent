package com.hugosol.chatagent.agent;

import com.hugosol.chatagent.agent.common.ErrorStrategy;
import com.hugosol.chatagent.agent.common.TaskContext;
import com.hugosol.chatagent.agent.common.TaskDefinition;
import com.hugosol.chatagent.agent.common.TaskName;
import com.hugosol.chatagent.agent.common.TaskRunner;
import com.hugosol.chatagent.config.AppProperties;
import com.hugosol.chatagent.config.PromptLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class LearningAgent {

    private static final Logger log = LoggerFactory.getLogger(LearningAgent.class);
    private final TaskRunner runner;

    public LearningAgent(TaskRunner runner, PromptLoader promptLoader, AppProperties appProperties) {
        this.runner = runner;
        String raw = promptLoader.load("memory-profile.txt");
        String template = raw.replace("{profileMaxLength}",
                String.valueOf(appProperties.getMemory().getProfileMaxLength()));
        runner.register(TaskName.MERGE_LEARNING, TaskDefinition
                .<MergeLearningParams, String>builder()
                .template(template)
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
        String result = runner.requestModel(TaskName.MERGE_LEARNING,
                new MergeLearningParams(oldProfile, errorSummary), ctx);
        return result != null ? result : "";
    }

    private record MergeLearningParams(String oldProfile, String errorSummary) {}
}
