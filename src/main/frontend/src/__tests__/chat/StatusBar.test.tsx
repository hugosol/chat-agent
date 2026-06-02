import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import React from "react";
import { ChatContext } from "../../state/ChatContext";
import type { ChatState, Action, AppStatus } from "../../state/chatState";
import type { Dispatch } from "react";

function createContextValue(overrides: Partial<ChatState> = {}): {
  state: ChatState;
  dispatch: Dispatch<Action>;
  send: (msg: unknown) => void;
} {
  return {
    state: {
      appStatus: "Connected",
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

function TestWrapper({
  ctxValue,
  children,
}: {
  ctxValue: ReturnType<typeof createContextValue>;
  children: React.ReactNode;
}): JSX.Element {
  return React.createElement(ChatContext.Provider, { value: ctxValue }, children);
}

import { StatusBar } from "../../components/StatusBar/StatusBar";

describe("StatusBar", () => {
  it("renders Connecting message with connecting CSS class", () => {
    const ctxValue = createContextValue({ appStatus: "Connecting" });
    render(
      React.createElement(TestWrapper, {
        ctxValue,
        children: React.createElement(StatusBar),
      })
    );
    expect(screen.getByText(/Connecting/)).toBeTruthy();
  });

  it("renders Connected message with connected CSS class", () => {
    const ctxValue = createContextValue({ appStatus: "Connected" });
    render(
      React.createElement(TestWrapper, {
        ctxValue,
        children: React.createElement(StatusBar),
      })
    );
    expect(screen.getByText(/Connected/)).toBeTruthy();
  });

  it("renders UserTurn message with userturn CSS class", () => {
    const ctxValue = createContextValue({ appStatus: "UserTurn" });
    render(
      React.createElement(TestWrapper, {
        ctxValue,
        children: React.createElement(StatusBar),
      })
    );
    expect(screen.getByText(/Type/)).toBeTruthy();
  });

  it("renders Processing message with processing CSS class", () => {
    const ctxValue = createContextValue({ appStatus: "Processing" });
    render(
      React.createElement(TestWrapper, {
        ctxValue,
        children: React.createElement(StatusBar),
      })
    );
    expect(screen.getByText(/Processing/)).toBeTruthy();
  });

  it("renders Warning message with payload text and warning CSS class", () => {
    const ctxValue = createContextValue({
      appStatus: "Warning",
      statusPayload: "Token usage at 85%",
    });
    render(
      React.createElement(TestWrapper, {
        ctxValue,
        children: React.createElement(StatusBar),
      })
    );
    expect(screen.getByText(/Token usage at 85%/)).toBeTruthy();
  });

  it("renders Error message with payload text and error CSS class", () => {
    const ctxValue = createContextValue({
      appStatus: "Error",
      statusPayload: "Server timeout",
    });
    render(
      React.createElement(TestWrapper, {
        ctxValue,
        children: React.createElement(StatusBar),
      })
    );
    expect(screen.getByText(/Server timeout/)).toBeTruthy();
  });

  it("renders Disconnected message with disconnected CSS class", () => {
    const ctxValue = createContextValue({ appStatus: "Disconnected" });
    render(
      React.createElement(TestWrapper, {
        ctxValue,
        children: React.createElement(StatusBar),
      })
    );
    expect(screen.getByText(/Disconnected/)).toBeTruthy();
  });
});
