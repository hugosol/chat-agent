package com.hugosol.chatagent.graph;

import com.hugosol.chatagent.dto.CorrectionData;
import com.hugosol.chatagent.dto.MemoryCueQueue;
import com.hugosol.chatagent.dto.MessageData;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChatState extends AgentState {

    public static final String SESSION_ID   = "sessionId";
    public static final String MODE         = "mode";
    public static final String USER_ID      = "userId";
    public static final String STATE_STATUS = "stateStatus";
    public static final String MESSAGES     = "messages";
    public static final String USER_INPUT   = "userInput";
    public static final String CORRECTIONS  = "corrections";
    public static final String LEARNING_PROFILE = "learningProfile";
    public static final String MEMORY_CUE_QUEUE = "memoryCueQueue";

    static final Map<String, Channel<?>> SCHEMA = Map.of(
            SESSION_ID,   Channels.base(() -> ""),
            MODE,         Channels.base(() -> ""),
            USER_ID,      Channels.base(() -> ""),
            STATE_STATUS, Channels.base(() -> "IDLE"),
            MESSAGES,     Channels.appender(ArrayList::new),
            USER_INPUT,   Channels.base(() -> ""),
            CORRECTIONS,  Channels.appender(ArrayList::new),
            LEARNING_PROFILE, Channels.base(() -> ""),
            MEMORY_CUE_QUEUE, Channels.base(MemoryCueQueue::new)
    );

    public ChatState(Map<String, Object> initData) {
        super(initData);
    }

    public static Map<String, Object> initialState(String sessionId, String mode, String userId,
                                                       String learningProfile,
                                                       MemoryCueQueue memoryCueQueue) {
        Map<String, Object> data = new HashMap<>();
        data.put(SESSION_ID, sessionId);
        data.put(MODE, mode);
        data.put(USER_ID, userId);
        data.put(STATE_STATUS, "IDLE");
        data.put(MESSAGES, new ArrayList<>());
        data.put(USER_INPUT, "");
        data.put(CORRECTIONS, new ArrayList<>());
        data.put(LEARNING_PROFILE, learningProfile != null ? learningProfile : "");
        data.put(MEMORY_CUE_QUEUE, memoryCueQueue);
        return data;
    }

    public String sessionId() {
        return this.<String>value(SESSION_ID).orElse("");
    }

    public String mode() {
        return this.<String>value(MODE).orElse("");
    }

    public String userId() {
        return this.<String>value(USER_ID).orElse("");
    }

    public String stateStatus() {
        return this.<String>value(STATE_STATUS).orElse("IDLE");
    }

    @SuppressWarnings("unchecked")
    public List<MessageData> messages() {
        return this.<List<MessageData>>value(MESSAGES).orElse(List.of());
    }

    public void addMessage(MessageData msg) {
        List<MessageData> list = this.<List<MessageData>>value(MESSAGES).orElse(null);
        if (list != null) {
            list.add(msg);
        }
    }

    public String userInput() {
        return this.<String>value(USER_INPUT).orElse("");
    }

    @SuppressWarnings("unchecked")
    public List<CorrectionData> corrections() {
        return this.<List<CorrectionData>>value(CORRECTIONS).orElse(List.of());
    }

    public void addCorrections(List<CorrectionData> corrections) {
        List<CorrectionData> list = this.<List<CorrectionData>>value(CORRECTIONS).orElse(null);
        if (list != null) {
            list.addAll(corrections);
        }
    }

    public String learningProfile() {
        return this.<String>value(LEARNING_PROFILE).orElse("");
    }

    public MemoryCueQueue memoryCueQueue() {
        return this.<MemoryCueQueue>value(MEMORY_CUE_QUEUE).orElse(null);
    }
}
