import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import React from "react";
import { ChatContext } from "../../state/ChatContext";
import type { ChatState } from "../../state/chatState";

function createCtx(overrides: Partial<ChatState> = {}) {
  return {
    state: {
      messages: [],
      corrections: [],
      tokenUsage: 0,
      connectionStatus: "connected" as const,
      streamInProgress: false,
      sessionStatus: "idle" as const,
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

import { Footer } from "../../components/chat/Footer";

describe("Footer", () => {
  let footer: HTMLElement;

  beforeEach(() => {
    const existing = document.querySelector("footer");
    if (!existing) {
      footer = document.createElement("footer");
      document.body.appendChild(footer);
    }
  });

  it("disables Start and enables End when session is active", () => {
    const ctx = createCtx({ sessionStatus: "active" });
    render(React.createElement(Wrapper, { ctx, children: React.createElement(Footer) }));
    const startBtn = screen.getByTestId("start-btn") as HTMLButtonElement;
    const endBtn = screen.getByTestId("end-btn") as HTMLButtonElement;
    expect(startBtn.disabled).toBe(true);
    expect(endBtn.disabled).toBe(false);
  });

  it("enables Start and disables End when session is idle", () => {
    const ctx = createCtx({ sessionStatus: "idle" });
    render(React.createElement(Wrapper, { ctx, children: React.createElement(Footer) }));
    const startBtn = screen.getByTestId("start-btn") as HTMLButtonElement;
    const endBtn = screen.getByTestId("end-btn") as HTMLButtonElement;
    expect(startBtn.disabled).toBe(false);
    expect(endBtn.disabled).toBe(true);
  });

  it("disables Mode Select when session is active", () => {
    const ctx = createCtx({ sessionStatus: "active" });
    render(React.createElement(Wrapper, { ctx, children: React.createElement(Footer) }));
    const modeSelect = screen.getByTestId("mode-select") as HTMLSelectElement;
    expect(modeSelect.disabled).toBe(true);
  });

  it("enables Mode Select when session is idle", () => {
    const ctx = createCtx({ sessionStatus: "idle" });
    render(React.createElement(Wrapper, { ctx, children: React.createElement(Footer) }));
    const modeSelect = screen.getByTestId("mode-select") as HTMLSelectElement;
    expect(modeSelect.disabled).toBe(false);
  });

  it("sends START_SESSION with selected mode on Start click", () => {
    const ctx = createCtx({ sessionStatus: "idle" });
    render(React.createElement(Wrapper, { ctx, children: React.createElement(Footer) }));
    const modeSelect = screen.getByTestId("mode-select") as HTMLSelectElement;
    fireEvent.change(modeSelect, { target: { value: "DAILY_TALK" } });
    fireEvent.click(screen.getByTestId("start-btn"));
    expect(ctx.send).toHaveBeenCalledWith({
      type: "START_SESSION",
      mode: "DAILY_TALK",
    });
  });

  it("sends END_SESSION on End click", () => {
    const ctx = createCtx({ sessionStatus: "active" });
    render(React.createElement(Wrapper, { ctx, children: React.createElement(Footer) }));
    fireEvent.click(screen.getByTestId("end-btn"));
    expect(ctx.send).toHaveBeenCalledWith({ type: "END_SESSION" });
  });
});
