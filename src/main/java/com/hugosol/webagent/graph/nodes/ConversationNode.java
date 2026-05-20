package com.hugosol.webagent.graph.nodes;

import com.hugosol.webagent.agent.ConversationAgent;
import com.hugosol.webagent.graph.CoachState;
import com.hugosol.webagent.graph.MessageData;
import org.bsc.langgraph4j.action.NodeAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class ConversationNode implements NodeAction<CoachState> {

    private static final Logger log = LoggerFactory.getLogger(ConversationNode.class);
    private final ConversationAgent conversationAgent;

    public ConversationNode(ConversationAgent conversationAgent) {
        this.conversationAgent = conversationAgent;
    }

    @Override
    public Map<String, Object> apply(CoachState state) throws Exception {
        log.info("ConversationNode: generating response for scenario={}", state.scenario());

        String userInput = state.userInput();
        List<MessageData> history = state.messages();
        String scenario = state.scenario();
        String persona = state.persona();

        String response = conversationAgent.generate(userInput, history, scenario, persona);

        MessageData agentMessage = new MessageData("AGENT", response);

        return Map.of(
                CoachState.CONVERSATION_TEXT, response,
                CoachState.MESSAGES, agentMessage,
                CoachState.STATE_STATUS, "PROCESSING"
        );
    }
}
