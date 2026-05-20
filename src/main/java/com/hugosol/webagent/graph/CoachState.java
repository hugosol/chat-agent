package com.hugosol.webagent.graph;

import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class CoachState extends AgentState {

    public static final String SESSION_ID   = "sessionId";
    public static final String SCENARIO     = "scenario";
    public static final String PERSONA      = "persona";
    public static final String STATE_STATUS = "stateStatus";
    public static final String MESSAGES     = "messages";
    public static final String USER_INPUT   = "userInput";
    public static final String CORRECTIONS  = "corrections";

    static final Map<String, Channel<?>> SCHEMA = Map.of(
            SESSION_ID,   Channels.base(() -> ""),
            SCENARIO,     Channels.base(() -> ""),
            PERSONA,      Channels.base(() -> ""),
            STATE_STATUS, Channels.base(() -> "IDLE"),
            MESSAGES,     Channels.appender(ArrayList::new),
            USER_INPUT,   Channels.base(() -> ""),
            CORRECTIONS,  Channels.appender(ArrayList::new)
    );

    public CoachState(Map<String, Object> initData) {
        super(initData);
    }

    public static Map<String, Object> initialState(String sessionId, String scenario, String persona) {
        return Map.of(
                SESSION_ID, sessionId,
                SCENARIO, scenario,
                PERSONA, persona,
                STATE_STATUS, "IDLE",
                MESSAGES, new ArrayList<>(),
                USER_INPUT, "",
                CORRECTIONS, new ArrayList<>()
        );
    }

    public String sessionId() {
        return this.<String>value(SESSION_ID).orElse("");
    }

    public String scenario() {
        return this.<String>value(SCENARIO).orElse("");
    }

    public String persona() {
        return this.<String>value(PERSONA).orElse("");
    }

    public String stateStatus() {
        return this.<String>value(STATE_STATUS).orElse("IDLE");
    }

    @SuppressWarnings("unchecked")
    public List<MessageData> messages() {
        return this.<List<MessageData>>value(MESSAGES).orElse(List.of());
    }

    public String userInput() {
        return this.<String>value(USER_INPUT).orElse("");
    }

    @SuppressWarnings("unchecked")
    public List<CorrectionData> corrections() {
        return this.<List<CorrectionData>>value(CORRECTIONS).orElse(List.of());
    }
}
