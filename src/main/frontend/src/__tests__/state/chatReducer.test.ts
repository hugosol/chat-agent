import { describe, it, expect } from "vitest";
import { chatReducer } from "../../state/chatReducer";
import type { ChatState } from "../../state/chatState";
import { initialState } from "../../state/chatState";

function seededState(): ChatState {
  return {
    messages: [
      { id: 1, role: "user", text: "Hello", streaming: false },
      { id: 2, role: "agent", text: "Hi there!", streaming: false },
    ],
    corrections: [
      {
        type: "GRAMMAR" as const,
        original: "I go",
        corrected: "I went",
        explanation: "Past tense",
        messageId: 1,
      },
    ],
    tokenUsage: 5000,
    connectionStatus: "connected",
    streamInProgress: false,
  };
}

describe("chatReducer", () => {
  it("SESSION_STARTED resets state to initial with connected status", () => {
    const state = chatReducer(seededState(), {
      type: "SESSION_STARTED",
      sessionId: "abc-123",
      mode: "DAILY_TALK",
    });

    expect(state).toEqual({
      ...initialState,
      connectionStatus: "connected",
    });
  });

  it("AGENT_STREAM_DELTA creates message with streaming flag on first delta", () => {
    const state = chatReducer(initialState, {
      type: "AGENT_STREAM_DELTA",
      messageId: 1,
      delta: "Sounds",
    });

    expect(state.messages).toHaveLength(1);
    expect(state.messages[0]).toEqual({
      id: 1,
      role: "agent",
      text: "Sounds",
      streaming: true,
    });
    expect(state.streamInProgress).toBe(true);
  });

  it("AGENT_STREAM_DELTA appends text to existing streaming message", () => {
    const first = chatReducer(initialState, {
      type: "AGENT_STREAM_DELTA",
      messageId: 1,
      delta: "Sounds",
    });

    const second = chatReducer(first, {
      type: "AGENT_STREAM_DELTA",
      messageId: 1,
      delta: " good!",
    });

    expect(second.messages).toHaveLength(1);
    expect(second.messages[0].text).toBe("Sounds good!");
    expect(second.messages[0].streaming).toBe(true);
  });

  it("AGENT_STREAM_END finalizes message and updates tokenUsage", () => {
    const state = chatReducer(initialState, {
      type: "AGENT_STREAM_DELTA",
      messageId: 1,
      delta: "Sounds good!",
    });

    const endState = chatReducer(state, {
      type: "AGENT_STREAM_END",
      messageId: 1,
      text: "Sounds good! Let's begin.",
      tokenUsage: 150,
    });

    expect(endState.messages[0].text).toBe("Sounds good! Let's begin.");
    expect(endState.messages[0].streaming).toBe(false);
    expect(endState.tokenUsage).toBe(150);
    expect(endState.streamInProgress).toBe(false);
  });

  it("CORRECTION_RESULT appends corrections array", () => {
    const state = chatReducer(initialState, {
      type: "CORRECTION_RESULT",
      messageId: 1,
      corrections: [
        {
          type: "GRAMMAR" as const,
          original: "I go",
          corrected: "I went",
          explanation: "Past tense",
          messageId: 1,
        },
        {
          type: "CHINGLISH" as const,
          original: "open light",
          corrected: "turn on light",
          explanation: "Use 'turn on'",
          messageId: 1,
        },
      ],
    });

    expect(state.corrections).toHaveLength(2);
    expect(state.corrections[0].type).toBe("GRAMMAR");
    expect(state.corrections[1].type).toBe("CHINGLISH");
  });

  it("STATE_UPDATE updates tokenUsage", () => {
    const state = chatReducer(initialState, {
      type: "STATE_UPDATE",
      state: "SPEAKING",
      tokenUsage: 320,
    });

    expect(state.tokenUsage).toBe(320);
  });

  it("SESSION_RESUMED batch rebuilds messages, corrections, and tokenUsage", () => {
    const state = chatReducer(initialState, {
      type: "SESSION_RESUMED",
      messages: [
        { role: "USER", content: "Hello", messageId: 1 },
        { role: "AGENT", content: "Hi there!", messageId: 1 },
      ],
      corrections: [
        {
          type: "WORD_CHOICE" as const,
          original: "big",
          corrected: "large",
          explanation: "",
          messageId: 2,
        },
      ],
      tokenUsage: 250,
    });

    expect(state.messages).toHaveLength(2);
    expect(state.messages[0]).toEqual({
      id: 1,
      role: "user",
      text: "Hello",
      streaming: false,
    });
    expect(state.messages[1]).toEqual({
      id: 1,
      role: "agent",
      text: "Hi there!",
      streaming: false,
    });
    expect(state.corrections).toHaveLength(1);
    expect(state.corrections[0].type).toBe("WORD_CHOICE");
    expect(state.tokenUsage).toBe(250);
    expect(state.connectionStatus).toBe("connected");
  });

  it("WS_CLOSED resets all state with disconnected status", () => {
    const state = chatReducer(seededState(), {
      type: "WS_CLOSED",
    });

    expect(state).toEqual({
      ...initialState,
      connectionStatus: "disconnected",
    });
  });
});
