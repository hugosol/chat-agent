import { describe, it, expect } from "vitest";
import { chatReducer } from "../../state/chatReducer";
import type { ChatState } from "../../state/chatState";
import { initialState } from "../../state/chatState";

describe("initialState", () => {
  it("has appStatus Connecting by default", () => {
    expect(initialState.appStatus).toBe("Connecting");
  });

  it("has statusPayload null by default", () => {
    expect(initialState.statusPayload).toBeNull();
  });

  it("has report null by default", () => {
    expect(initialState.report).toBeNull();
  });
  it("has mode empty string by default", () => {
    expect(initialState.mode).toBe("");
  });
});

function seededState(): ChatState {
  return {
    appStatus: "UserTurn",
    statusPayload: null,
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
    streamInProgress: false,
    report: null,
    mode: "WORKPLACE_STANDUP",
  };
}

describe("chatReducer", () => {
  it("SESSION_STARTED resets state and sets appStatus to UserTurn", () => {
    const state = chatReducer(seededState(), {
      type: "SESSION_STARTED",
      sessionId: "abc-123",
      mode: "DAILY_TALK",
    });

    expect(state).toEqual({
      ...initialState,
      appStatus: "UserTurn",
      mode: "DAILY_TALK",
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

  it("SESSION_RESUMED batch rebuilds messages, corrections, tokenUsage, and sets active", () => {
    const state = chatReducer(initialState, {
      type: "SESSION_RESUMED",
      mode: "WORKPLACE_STANDUP",
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
    expect(state.appStatus).toBe("UserTurn");
    expect(state.mode).toBe("WORKPLACE_STANDUP");
  });

  it("WS_CLOSED resets all state with disconnected and idle status", () => {
    const state = chatReducer(seededState(), {
      type: "WS_CLOSED",
    });

    expect(state.appStatus).toBe("Disconnected");
    expect(state.messages).toEqual([]);
    expect(state.corrections).toEqual([]);
  });

  it("USER_MESSAGE_SENT appends a user message to the messages array", () => {
    const state = chatReducer(initialState, {
      type: "USER_MESSAGE_SENT",
      messageId: 1,
      text: "Hello Coach",
    });

    expect(state.messages).toHaveLength(1);
    expect(state.messages[0]).toEqual({
      id: 1,
      role: "user",
      text: "Hello Coach",
      streaming: false,
    });
    expect(state.appStatus).toBe("Connecting");
  });

  it("USER_MESSAGE_SENT appends after existing messages with correct next id", () => {
    const withMessages = chatReducer(initialState, {
      type: "SESSION_RESUMED",
      mode: "",
      messages: [
        { role: "USER", content: "First", messageId: 1 },
        { role: "AGENT", content: "Reply", messageId: 1 },
      ],
      corrections: [],
      tokenUsage: 0,
    });

    const state = chatReducer(withMessages, {
      type: "USER_MESSAGE_SENT",
      messageId: 2,
      text: "Second",
    });

    expect(state.messages).toHaveLength(3);
    expect(state.messages[2]).toEqual({
      id: 2,
      role: "user",
      text: "Second",
      streaming: false,
    });
  });

  it("SET_APP_STATUS updates appStatus and statusPayload", () => {
    const state = chatReducer(initialState, {
      type: "SET_APP_STATUS",
      appStatus: "Warning",
      statusPayload: "Token usage at 85%",
    });

    expect(state.appStatus).toBe("Warning");
    expect(state.statusPayload).toBe("Token usage at 85%");
  });

  it("SET_APP_STATUS clears statusPayload when not provided", () => {
    const withPayload = chatReducer(initialState, {
      type: "SET_APP_STATUS",
      appStatus: "Warning",
      statusPayload: "old warning",
    });

    const state = chatReducer(withPayload, {
      type: "SET_APP_STATUS",
      appStatus: "UserTurn",
    });

    expect(state.appStatus).toBe("UserTurn");
    expect(state.statusPayload).toBeNull();
  });

  it("SESSION_REPORT resets to initial state with Connected appStatus and stores report", () => {
    const streaming = chatReducer(initialState, {
      type: "AGENT_STREAM_DELTA",
      messageId: 1,
      delta: "Hello",
    });

    const state = chatReducer(streaming, {
      type: "SESSION_REPORT",
      report: { score: 85 },
    });

    expect(state.appStatus).toBe("Connected");
    expect(state.streamInProgress).toBe(false);
    expect(state.report).toEqual({ score: 85 });
  });

  it("DISMISS_REPORT sets report to null", () => {
    const withReport = chatReducer(initialState, {
      type: "SESSION_REPORT",
      report: { score: 85 },
    });

    const state = chatReducer(withReport, {
      type: "DISMISS_REPORT",
    });

    expect(state.report).toBeNull();
    expect(state.appStatus).toBe("Connected");
  });
});
