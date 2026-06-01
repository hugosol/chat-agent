import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen } from "@testing-library/react";
import React from "react";
import { ChatContext } from "../../state/ChatContext";
import type { ChatState, Action } from "../../state/chatState";
import type { Dispatch } from "react";

function createContextValue(overrides: Partial<ChatState> = {}): {
  state: ChatState;
  dispatch: Dispatch<Action>;
  send: (msg: unknown) => void;
} {
  return {
    state: {
      messages: [],
      corrections: [],
      tokenUsage: 0,
      connectionStatus: "connected",
      streamInProgress: false,
      sessionStatus: "active",
      ...overrides,
    },
    dispatch: vi.fn(),
    send: vi.fn(),
  };
}

function TestWrapper({
  ctxValue,
  children,
}: {
  ctxValue: ReturnType<typeof createContextValue>;
  children: React.ReactNode;
}): JSX.Element {
  return React.createElement(ChatContext.Provider, { value: ctxValue }, children);
}

import { MessageList } from "../../components/chat/MessageList";

describe("MessageList", () => {
  beforeEach(() => {
    const existing = document.getElementById("messages");
    if (!existing) {
      const div = document.createElement("div");
      div.id = "messages";
      document.body.appendChild(div);
    }
  });

  it("renders a user message bubble with data-testid and data-role", () => {
    const ctxValue = createContextValue({
      messages: [{ id: 1, role: "user", text: "Hello Coach", streaming: false }],
    });
    render(
      React.createElement(TestWrapper, { ctxValue }, React.createElement(MessageList))
    );
    const msg = screen.getByTestId("message");
    expect(msg.getAttribute("data-role")).toBe("user");
    expect(msg.textContent).toContain("Hello Coach");
  });

  it("renders an agent message bubble with data-testid and data-role", () => {
    const ctxValue = createContextValue({
      messages: [{ id: 1, role: "agent", text: "Hi there!", streaming: false }],
    });
    render(
      React.createElement(TestWrapper, { ctxValue }, React.createElement(MessageList))
    );
    const msg = screen.getByTestId("message");
    expect(msg.getAttribute("data-role")).toBe("agent");
    expect(msg.textContent).toContain("Hi there!");
  });

  it("shows streaming cursor when agent message is streaming", () => {
    const ctxValue = createContextValue({
      messages: [{ id: 1, role: "agent", text: "Partial", streaming: true }],
    });
    render(
      React.createElement(TestWrapper, { ctxValue }, React.createElement(MessageList))
    );
    const cursor = screen.getByTestId("stream-cursor");
    expect(cursor).toBeInTheDocument();
  });

  it("does not show streaming cursor when agent message streaming is false", () => {
    const ctxValue = createContextValue({
      messages: [{ id: 1, role: "agent", text: "Done", streaming: false }],
    });
    render(
      React.createElement(TestWrapper, { ctxValue }, React.createElement(MessageList))
    );
    expect(screen.queryByTestId("stream-cursor")).toBeNull();
  });

  it("shows play button when agent message is not streaming", () => {
    const ctxValue = createContextValue({
      messages: [{ id: 1, role: "agent", text: "Done", streaming: false }],
    });
    render(
      React.createElement(TestWrapper, { ctxValue }, React.createElement(MessageList))
    );
    expect(screen.getByTestId("play-button")).toBeInTheDocument();
  });

  it("does not show play button on user messages", () => {
    const ctxValue = createContextValue({
      messages: [{ id: 1, role: "user", text: "Hello", streaming: false }],
    });
    render(
      React.createElement(TestWrapper, { ctxValue }, React.createElement(MessageList))
    );
    expect(screen.queryByTestId("play-button")).toBeNull();
  });

  it("renders correction bubbles after their matching user message", () => {
    const ctxValue = createContextValue({
      messages: [
        { id: 1, role: "user", text: "I go", streaming: false },
        { id: 2, role: "agent", text: "Reply", streaming: false },
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
    });
    render(
      React.createElement(TestWrapper, { ctxValue }, React.createElement(MessageList))
    );
    const bubbles = screen.getAllByTestId("correction-bubble");
    expect(bubbles).toHaveLength(1);
    expect(bubbles[0].textContent).toContain("I go");
    expect(bubbles[0].textContent).toContain("I went");
  });

  it("renders numbered summary in correction bubble", () => {
    const ctxValue = createContextValue({
      messages: [{ id: 1, role: "user", text: "Hello", streaming: false }],
      corrections: [
        {
          type: "GRAMMAR" as const,
          original: "I go",
          corrected: "I went",
          explanation: "",
          messageId: 1,
        },
        {
          type: "WORD_CHOICE" as const,
          original: "big",
          corrected: "large",
          explanation: "",
          messageId: 1,
        },
      ],
    });
    render(
      React.createElement(TestWrapper, { ctxValue }, React.createElement(MessageList))
    );
    const bubbles = screen.getAllByTestId("correction-bubble");
    expect(bubbles).toHaveLength(1);
    const content = bubbles[0].textContent || "";
    expect(content).toMatch(/1\..*I go.*I went/);
    expect(content).toMatch(/2\..*big.*large/);
  });

  it("shows Show earlier button when messages exceed 10", () => {
    const msgs: Array<{ id: number; role: "user" | "agent"; text: string; streaming: boolean }> = [];
    for (let i = 1; i <= 12; i++) {
      msgs.push({ id: i, role: i % 2 === 1 ? "user" : "agent", text: "Msg " + i, streaming: false });
    }
    const ctxValue = createContextValue({ messages: msgs });
    render(
      React.createElement(TestWrapper, { ctxValue }, React.createElement(MessageList))
    );
    expect(screen.getByTestId("show-earlier-btn")).toBeInTheDocument();
    const allMsgs = screen.getAllByTestId("message");
    expect(allMsgs.length).toBeLessThan(12);
  });
});
