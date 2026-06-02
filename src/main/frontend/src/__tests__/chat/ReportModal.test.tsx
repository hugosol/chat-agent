import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
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

import { ReportModal } from "../../components/ReportModal/ReportModal";

describe("ReportModal", () => {
  it("does not render when report is null", () => {
    const ctx = createCtx({ report: null });
    const { container } = render(
      React.createElement(Wrapper, { ctx, children: React.createElement(ReportModal) })
    );
    expect(container.innerHTML).toBe("");
  });

  it("renders report content when report is present", () => {
    const ctx = createCtx({
      report: {
        overallAssessment: "Great session!",
        fluencyScore: 85,
        errorSummary: "2 grammar, 1 word choice",
        keyTakeaway: "Practice past tense",
      },
    });
    render(React.createElement(Wrapper, { ctx, children: React.createElement(ReportModal) }));
    expect(screen.getByText(/Great session/)).toBeTruthy();
    expect(screen.getByText(/Overall Assessment/)).toBeTruthy();
    expect(screen.getByText(/2 grammar/)).toBeTruthy();
    expect(screen.getByText(/Practice past tense/)).toBeTruthy();
  });

  it("shows fluency score when score >= 0", () => {
    const ctx = createCtx({
      report: { overallAssessment: "Test", fluencyScore: 85 },
    });
    render(React.createElement(Wrapper, { ctx, children: React.createElement(ReportModal) }));
    expect(screen.getByText("85")).toBeTruthy();
  });

  it("hides fluency score when score < 0", () => {
    const ctx = createCtx({
      report: { overallAssessment: "Test", fluencyScore: -1 },
    });
    render(React.createElement(Wrapper, { ctx, children: React.createElement(ReportModal) }));
    expect(screen.queryByText("Fluency Score")).toBeFalsy();
  });

  it("dispatches DISMISS_REPORT on close button click", () => {
    const ctx = createCtx({
      report: { overallAssessment: "Test", fluencyScore: 80 },
    });
    render(React.createElement(Wrapper, { ctx, children: React.createElement(ReportModal) }));
    fireEvent.click(screen.getByTestId("report-close-btn"));
    expect(ctx.dispatch).toHaveBeenCalledWith({ type: "DISMISS_REPORT" });
  });

  it("has report-modal data-testid with aria-hidden false when visible", () => {
    const ctx = createCtx({
      report: { overallAssessment: "Test", fluencyScore: 80 },
    });
    render(React.createElement(Wrapper, { ctx, children: React.createElement(ReportModal) }));
    const modal = screen.getByTestId("report-modal");
    expect(modal.getAttribute("aria-hidden")).toBe("false");
  });

  it("has report-content data-testid", () => {
    const ctx = createCtx({
      report: { overallAssessment: "Test", fluencyScore: 80 },
    });
    render(React.createElement(Wrapper, { ctx, children: React.createElement(ReportModal) }));
    expect(screen.getByTestId("report-content")).toBeTruthy();
  });

  it("hides fluency section when fluencyScore is undefined", () => {
    const ctx = createCtx({
      report: { overallAssessment: "Test" },
    });
    render(React.createElement(Wrapper, { ctx, children: React.createElement(ReportModal) }));
    expect(screen.queryByText("Fluency Score")).toBeFalsy();
  });
});
