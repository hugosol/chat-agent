package com.hugosol.chatagent.graph.nodes;

import com.hugosol.chatagent.agent.CorrectionAgent;
import com.hugosol.chatagent.agent.common.TaskContext;
import com.hugosol.chatagent.graph.ChatState;
import com.hugosol.chatagent.dto.CorrectionData;
import org.bsc.langgraph4j.action.NodeAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class CorrectionNode implements NodeAction<ChatState> {

    private static final Logger log = LoggerFactory.getLogger(CorrectionNode.class);
    private final CorrectionAgent correctionAgent;

    public CorrectionNode(CorrectionAgent correctionAgent) {
        this.correctionAgent = correctionAgent;
    }

    @Override
    public Map<String, Object> apply(ChatState state) throws Exception {
        log.info("CorrectionNode: analyzing user input");

        String userInput = state.userInput();
        TaskContext ctx = new TaskContext(state.sessionId(), state.userId(), state.mode());
        List<CorrectionData> corrections = correctionAgent.analyze(userInput, ctx);

        if (!corrections.isEmpty()) {
            log.info("CorrectionNode: found {} corrections", corrections.size());
        }

        return Map.of(ChatState.CORRECTIONS, corrections);
    }
}
