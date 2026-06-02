import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import React from "react";
import { ChatContext } from "../../state/ChatContext";
import type { ChatState, Action } from "../../state/chatState";
import type { Dispatch } from "react";

function createCtx(overrides: Partial<ChatState> = {}) {
  return {
    state: {
      messages: [],
      corrections: [],
      tokenUsage: 0,
      connectionStatus: "connected" as const,
      streamInProgress: false,
      sessionStatus: "active" as const,
      ...overrides,
    },
    dispatch: vi.fn(),
    send: vi.fn(),
  };
}

function Wrapper({
  ctx,
  children,
}: {
  ctx: ReturnType<typeof createCtx>;
  children: React.ReactNode;
}) {
  return React.createElement(ChatContext.Provider, { value: ctx }, children);
}

import { ChatInput } from "../../components/chat/ChatInput";

describe("ChatInput", () => {
  let textInputBar: HTMLDivElement;

  beforeEach(() => {
    const existing = document.getElementById("textInputBar");
    if (existing) existing.remove();
    textInputBar = document.createElement("div");
    textInputBar.id = "textInputBar";
    document.body.appendChild(textInputBar);
    const input = document.createElement("input");
    input.id = "textInput";
    textInputBar.appendChild(input);
    const btn = document.createElement("button");
    btn.id = "sendTextBtn";
    textInputBar.appendChild(btn);
  });

  it("is disabled when sessionStatus is idle", () => {
    const ctx = createCtx({ sessionStatus: "idle" });
    render(React.createElement(Wrapper, { ctx, children: React.createElement(ChatInput) }));
    const input = screen.getByTestId("chat-text-input") as HTMLInputElement;
    expect(input.disabled).toBe(true);
  });

  it("is disabled when streamInProgress is true", () => {
    const ctx = createCtx({ streamInProgress: true });
    render(React.createElement(Wrapper, { ctx, children: React.createElement(ChatInput) }));
    const input = screen.getByTestId("chat-text-input") as HTMLInputElement;
    expect(input.disabled).toBe(true);
  });

  it("is enabled when sessionStatus is active and not streaming", () => {
    const ctx = createCtx({ sessionStatus: "active", streamInProgress: false });
    render(React.createElement(Wrapper, { ctx, children: React.createElement(ChatInput) }));
    const input = screen.getByTestId("chat-text-input") as HTMLInputElement;
    expect(input.disabled).toBe(false);
  });

  it("sends USER_INPUT and dispatches USER_MESSAGE_SENT on Enter", () => {
    const ctx = createCtx({ sessionStatus: "active" });
    render(React.createElement(Wrapper, { ctx, children: React.createElement(ChatInput) }));
    const input = screen.getByTestId("chat-text-input") as HTMLInputElement;
    fireEvent.change(input, { target: { value: "Hello Coach" } });
    fireEvent.keyDown(input, { key: "Enter" });
    expect(ctx.send).toHaveBeenCalledWith({
      type: "USER_INPUT",
      text: "Hello Coach",
      messageId: 1,
    });
    expect(ctx.dispatch).toHaveBeenCalledWith({
      type: "USER_MESSAGE_SENT",
      messageId: 1,
      text: "Hello Coach",
    });
  });

  it("does not send when input is empty", () => {
    const ctx = createCtx({ sessionStatus: "active" });
    render(React.createElement(Wrapper, { ctx, children: React.createElement(ChatInput) }));
    const input = screen.getByTestId("chat-text-input") as HTMLInputElement;
    fireEvent.keyDown(input, { key: "Enter" });
    expect(ctx.send).not.toHaveBeenCalled();
    expect(ctx.dispatch).not.toHaveBeenCalled();
  });

  it("derives correct messageId from existing user messages", () => {
    const ctx = createCtx({
      sessionStatus: "active",
      messages: [
        { id: 1, role: "user", text: "First", streaming: false },
        { id: 1, role: "agent", text: "Reply", streaming: false },
        { id: 2, role: "user", text: "Second", streaming: false },
      ],
    });
    render(React.createElement(Wrapper, { ctx, children: React.createElement(ChatInput) }));
    const input = screen.getByTestId("chat-text-input") as HTMLInputElement;
    fireEvent.change(input, { target: { value: "Third" } });
    fireEvent.keyDown(input, { key: "Enter" });
    expect(ctx.send).toHaveBeenCalledWith({
      type: "USER_INPUT",
      text: "Third",
      messageId: 3,
    });
  });
});
