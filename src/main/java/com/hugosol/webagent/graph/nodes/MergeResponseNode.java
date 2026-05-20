package com.hugosol.webagent.graph.nodes;

import com.hugosol.webagent.graph.CoachState;
import com.hugosol.webagent.graph.CorrectionData;
import org.bsc.langgraph4j.action.NodeAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class MergeResponseNode implements NodeAction<CoachState> {

    private static final Logger log = LoggerFactory.getLogger(MergeResponseNode.class);

    private static final int CHARS_PER_TOKEN = 4;

    @Override
    public Map<String, Object> apply(CoachState state) {
        log.info("MergeResponseNode: merging conversation and corrections");

        String conversationText = state.conversationText();
        List<CorrectionData> corrections = state.corrections();

        StringBuilder merged = new StringBuilder(conversationText);

        if (!corrections.isEmpty()) {
            merged.append("\n\n");
            for (CorrectionData c : corrections) {
                merged.append(String.format("%s -> %s\n",
                        c.getOriginal(), c.getCorrected()));
            }
        }

        String mergedText = merged.toString();
        int estimatedTokens = estimateTokens(mergedText);
        int totalTokens = state.tokenCount() + estimatedTokens;

        log.info("MergeResponseNode: estimated tokens this turn={}, total={}", estimatedTokens, totalTokens);

        return Map.of(
                CoachState.MERGED_RESPONSE, mergedText,
                CoachState.TOKEN_COUNT, totalTokens,
                CoachState.STATE_STATUS, "SPEAKING"
        );
    }

    private int estimateTokens(String text) {
        return Math.max(1, text.length() / CHARS_PER_TOKEN);
    }
}
