import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import React from "react";
import { ChatContext } from "../../state/ChatContext";
import type { ChatState } from "../../state/chatState";

function createCtx(overrides: Partial<ChatState> = {}) {
  return {
    state: {
      appStatus: "Connected" as const,
      statusPayload: null,
      messages: [],
      corrections: [],
      tokenUsage: 0,
      streamInProgress: false,
      report: null,
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

import { Footer } from "../../components/Footer/Footer";

const MODES_FIXTURE = [
  { name: "WORKPLACE_STANDUP", displayName: "Standup Meeting" },
  { name: "DAILY_TALK", displayName: "Daily Talk" },
];

describe("Footer", () => {
  it("disables Start and enables End when session is active (UserTurn)", () => {
    const ctx = createCtx({ appStatus: "UserTurn" });
    render(React.createElement(Wrapper, { ctx, children: React.createElement(Footer) }));
    const startBtn = screen.getByTestId("start-btn") as HTMLButtonElement;
    const endBtn = screen.getByTestId("end-btn") as HTMLButtonElement;
    expect(startBtn.disabled).toBe(true);
    expect(endBtn.disabled).toBe(false);
  });

  it("enables Start and disables End when session is not active (Connected) and modes loaded", async () => {
    globalThis.fetch = vi.fn().mockResolvedValue({
      json: () => Promise.resolve(MODES_FIXTURE),
    });
    const ctx = createCtx({ appStatus: "Connected" });
    render(React.createElement(Wrapper, { ctx, children: React.createElement(Footer) }));
    await waitFor(() => {
      const startBtn = screen.getByTestId("start-btn") as HTMLButtonElement;
      expect(startBtn.disabled).toBe(false);
    });
    const endBtn = screen.getByTestId("end-btn") as HTMLButtonElement;
    expect(endBtn.disabled).toBe(true);
  });

  it("disables Mode Select when session is active", () => {
    const ctx = createCtx({ appStatus: "UserTurn" });
    render(React.createElement(Wrapper, { ctx, children: React.createElement(Footer) }));
    const modeSelect = screen.getByTestId("mode-select") as HTMLSelectElement;
    expect(modeSelect.disabled).toBe(true);
  });

  it("enables Mode Select when session is not active and modes loaded", async () => {
    globalThis.fetch = vi.fn().mockResolvedValue({
      json: () => Promise.resolve(MODES_FIXTURE),
    });
    const ctx = createCtx({ appStatus: "Connected" });
    render(React.createElement(Wrapper, { ctx, children: React.createElement(Footer) }));
    await waitFor(() => {
      const modeSelect = screen.getByTestId("mode-select") as HTMLSelectElement;
      expect(modeSelect.disabled).toBe(false);
    });
  });

  it("sends START_SESSION with selected mode on Start click", async () => {
    globalThis.fetch = vi.fn().mockResolvedValue({
      json: () => Promise.resolve(MODES_FIXTURE),
    });
    const ctx = createCtx({ appStatus: "Connected" });
    render(React.createElement(Wrapper, { ctx, children: React.createElement(Footer) }));
    await waitFor(() => {
      expect(screen.getByText("Daily Talk")).toBeInTheDocument();
    });
    const modeSelect = screen.getByTestId("mode-select") as HTMLSelectElement;
    fireEvent.change(modeSelect, { target: { value: "DAILY_TALK" } });
    fireEvent.click(screen.getByTestId("start-btn"));
    expect(ctx.send).toHaveBeenCalledWith({
      type: "START_SESSION",
      mode: "DAILY_TALK",
    });
  });

  it("sends END_SESSION on End click", () => {
    const ctx = createCtx({ appStatus: "UserTurn" });
    render(React.createElement(Wrapper, { ctx, children: React.createElement(Footer) }));
    fireEvent.click(screen.getByTestId("end-btn"));
    expect(ctx.send).toHaveBeenCalledWith({ type: "END_SESSION" });
  });

  it("keeps Start and select disabled when API fails", async () => {
    globalThis.fetch = vi.fn().mockRejectedValue(new Error("fetch unavailable"));
    const ctx = createCtx({ appStatus: "Connected" });
    render(React.createElement(Wrapper, { ctx, children: React.createElement(Footer) }));
    const startBtn = screen.getByTestId("start-btn") as HTMLButtonElement;
    const modeSelect = screen.getByTestId("mode-select") as HTMLSelectElement;
    expect(startBtn.disabled).toBe(true);
    expect(modeSelect.disabled).toBe(true);
  });
});
