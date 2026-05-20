package com.hugosol.webagent.graph;

import com.hugosol.webagent.graph.nodes.ConversationNode;
import com.hugosol.webagent.graph.nodes.CorrectionNode;
import com.hugosol.webagent.graph.nodes.MergeResponseNode;
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
public class CoachGraphBuilder {

    private static final Logger log = LoggerFactory.getLogger(CoachGraphBuilder.class);

    private final CompiledGraph<CoachState> compiledGraph;

    public CoachGraphBuilder(ConversationNode conversationNode,
                             CorrectionNode correctionNode,
                             MergeResponseNode mergeResponseNode) {
        try {
            var graph = new StateGraph<>(CoachState.SCHEMA, CoachState::new)
                    .addNode("conversation", node_async(conversationNode))
                    .addNode("correction", node_async(correctionNode))
                    .addNode("merge", node_async(mergeResponseNode))
                    .addEdge(START, "conversation")
                    .addEdge("conversation", "correction")
                    .addEdge("correction", "merge")
                    .addEdge("merge", END);

            this.compiledGraph = graph.compile(CompileConfig.builder()
                    .checkpointSaver(new MemorySaver())
                    .build());

            log.info("CoachGraphBuilder: graph compiled successfully");
        } catch (GraphStateException e) {
            log.error("CoachGraphBuilder: failed to compile graph", e);
            throw new RuntimeException("Failed to compile CoachGraph", e);
        }
    }

    public CompiledGraph<CoachState> getCompiledGraph() {
        return compiledGraph;
    }
}
