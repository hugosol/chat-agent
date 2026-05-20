package com.hugosol.webagent.graph.nodes;

import com.hugosol.webagent.agent.CorrectionAgent;
import com.hugosol.webagent.graph.CoachState;
import com.hugosol.webagent.graph.CorrectionData;
import com.hugosol.webagent.graph.MessageData;
import org.bsc.langgraph4j.action.NodeAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class CorrectionNode implements NodeAction<CoachState> {

    private static final Logger log = LoggerFactory.getLogger(CorrectionNode.class);
    private final CorrectionAgent correctionAgent;

    public CorrectionNode(CorrectionAgent correctionAgent) {
        this.correctionAgent = correctionAgent;
    }

    @Override
    public Map<String, Object> apply(CoachState state) throws Exception {
        log.info("CorrectionNode: analyzing user input");

        String userInput = state.userInput();
        List<CorrectionData> corrections = correctionAgent.analyze(userInput);

        if (!corrections.isEmpty()) {
            log.info("CorrectionNode: found {} corrections", corrections.size());

            StringBuilder correctionText = new StringBuilder();
            for (CorrectionData c : corrections) {
                correctionText.append(String.format("[%s] %s -> %s (%s)\n",
                        c.getType(), c.getOriginal(), c.getCorrected(), c.getExplanation()));
            }
            MessageData correctionMessage = new MessageData("CORRECTION", correctionText.toString().trim());
            return Map.of(
                    CoachState.CORRECTIONS, corrections,
                    CoachState.MESSAGES, correctionMessage
            );
        }

        return Map.of(CoachState.CORRECTIONS, corrections);
    }
}
