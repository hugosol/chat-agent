package com.hugosol.webagent.service;

import com.hugosol.webagent.agent.ReportAgent;
import com.hugosol.webagent.agent.ReportAgent.ReportResult;
import com.hugosol.webagent.graph.CoachGraphBuilder;
import com.hugosol.webagent.graph.CoachState;
import com.hugosol.webagent.graph.CorrectionData;
import com.hugosol.webagent.graph.MessageData;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.RunnableConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class GraphExecutionService {

    private static final Logger log = LoggerFactory.getLogger(GraphExecutionService.class);

    private final CompiledGraph<CoachState> graph;
    private final ReportAgent reportAgent;
    private final Map<String, CoachState> activeStates = new ConcurrentHashMap<>();

    public GraphExecutionService(CoachGraphBuilder graphBuilder, ReportAgent reportAgent) {
        this.graph = graphBuilder.getCompiledGraph();
        this.reportAgent = reportAgent;
    }

    public void initSession(String sessionId, String scenario, String persona) {
        Map<String, Object> initData = CoachState.initialState(sessionId, scenario, persona);
        var state = new CoachState(initData);
        activeStates.put(sessionId, state);
        log.info("GraphExecutionService: initialized session {}", sessionId);
    }

    public TurnResult processTurn(String sessionId, String userInput) {
        CoachState state = activeStates.get(sessionId);
        if (state == null) {
            throw new IllegalStateException("No active session: " + sessionId);
        }

        MessageData userMessage = new MessageData("USER", userInput);

        Map<String, Object> input = Map.of(
                CoachState.USER_INPUT, userInput,
                CoachState.MESSAGES, userMessage,
                CoachState.STATE_STATUS, "PROCESSING"
        );

        var config = RunnableConfig.builder()
                .threadId(sessionId)
                .build();

        CoachState finalState = null;
        for (var item : graph.stream(input, config)) {
            finalState = item.state();
        }

        if (finalState == null) {
            throw new RuntimeException("Graph execution produced no result");
        }

        activeStates.put(sessionId, finalState);

        String merged = finalState.mergedResponse();
        List<CorrectionData> corrections = finalState.corrections();
        int tokenCount = finalState.tokenCount();

        return new TurnResult(merged, corrections, tokenCount);
    }

    public ReportResult generateReport(String sessionId) {
        CoachState state = activeStates.get(sessionId);
        if (state == null) {
            throw new IllegalStateException("No active session: " + sessionId);
        }
        List<MessageData> messages = new ArrayList<>(state.messages());
        List<CorrectionData> corrections = state.corrections();
        return reportAgent.generate(messages, corrections);
    }

    public List<MessageData> getSessionMessages(String sessionId) {
        CoachState state = activeStates.get(sessionId);
        if (state == null) {
            return List.of();
        }
        return state.messages();
    }

    public int getTokenCount(String sessionId) {
        CoachState state = activeStates.get(sessionId);
        if (state == null) return 0;
        return state.tokenCount();
    }

    public List<CorrectionData> getSessionCorrections(String sessionId) {
        CoachState state = activeStates.get(sessionId);
        if (state == null) return List.of();
        return state.corrections();
    }

    public void removeSession(String sessionId) {
        activeStates.remove(sessionId);
        log.info("GraphExecutionService: removed session {}", sessionId);
    }

    public record TurnResult(
            String mergedResponse,
            List<CorrectionData> corrections,
            int tokenCount
    ) {}
}
