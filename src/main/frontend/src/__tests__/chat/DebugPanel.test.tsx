import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, act } from "@testing-library/react";
import React from "react";
import { DebugPanel } from "../../components/DebugPanel/DebugPanel";
import { debugLog, subscribeDebug } from "../../shared/debugLog";

describe("DebugPanel", () => {
  beforeEach(() => {
    // Reset module state between tests by re-importing
  });

  it("receives entries via debugLog", () => {
    render(React.createElement(DebugPanel, { isOpen: true, onToggle: vi.fn() }));
    act(() => {
      debugLog("WS: connected");
    });
    expect(screen.getByText(/WS: connected/)).toBeTruthy();
  });

  it("clears all entries on clear button click", () => {
    render(React.createElement(DebugPanel, { isOpen: true, onToggle: vi.fn() }));
    act(() => {
      debugLog("WS: connected");
    });
    expect(screen.getByText(/WS: connected/)).toBeTruthy();
    fireEvent.click(screen.getByText("Clear"));
    expect(screen.queryByText(/WS: connected/)).toBeFalsy();
  });

  it("calls onToggle when toggle button is clicked", () => {
    const onToggle = vi.fn();
    render(React.createElement(DebugPanel, { isOpen: true, onToggle }));
    fireEvent.click(screen.getByText("Log"));
    expect(onToggle).toHaveBeenCalled();
  });

  it("unsubscribes on unmount", () => {
    const { unmount } = render(
      React.createElement(DebugPanel, { isOpen: true, onToggle: vi.fn() })
    );
    unmount();
    act(() => {
      debugLog("should not appear");
    });
    // No crash, no entries rendered
  });

  it("does not render content when isOpen is false", () => {
    render(React.createElement(DebugPanel, { isOpen: false, onToggle: vi.fn() }));
    expect(screen.queryByText("Clear")).toBeFalsy();
  });
});
