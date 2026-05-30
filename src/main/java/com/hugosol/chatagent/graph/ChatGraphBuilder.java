package com.hugosol.chatagent.graph;

import com.hugosol.chatagent.graph.nodes.CorrectionNode;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.checkpoint.MemorySaver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

@Component
public class ChatGraphBuilder {

    private static final Logger log = LoggerFactory.getLogger(ChatGraphBuilder.class);

    private final CompiledGraph<ChatState> compiledGraph;

    public ChatGraphBuilder(CorrectionNode correctionNode) {
        try {
            var graph = new StateGraph<>(ChatState.SCHEMA, ChatState::new)
                    .addNode("correction", node_async(correctionNode))
                    .addEdge(START, "correction")
                    .addEdge("correction", END);

            this.compiledGraph = graph.compile(CompileConfig.builder()
                    .checkpointSaver(new MemorySaver())
                    .build());

            log.info("ChatGraphBuilder: graph compiled successfully");
        } catch (GraphStateException e) {
            log.error("ChatGraphBuilder: failed to compile graph", e);
            throw new RuntimeException("Failed to compile CoachGraph", e);
        }
    }

    public CompiledGraph<ChatState> getCompiledGraph() {
        return compiledGraph;
    }
}
