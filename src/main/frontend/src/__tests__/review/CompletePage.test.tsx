import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { CompletePage } from "../../components/review/CompletePage";
import type { ReviewStats } from "../../components/review/reviewTypes";

function makeDate(offsetMs: number): string {
  return new Date(Date.now() + offsetMs).toISOString();
}

function normalStats(overrides: Partial<ReviewStats> = {}): ReviewStats {
  return {
    reviewedToday: 8,
    remaining: 2,
    learnedToday: 3,
    dailyLimit: 20,
    nextDueAt: makeDate(3 * 3600 * 1000),
    ...overrides,
  };
}

describe("CompletePage", () => {
  it("renders reviewedToday and learnedToday counts", () => {
    render(<CompletePage stats={normalStats()} onBack={() => {}} />);

    expect(screen.getByText("8")).toBeTruthy();
    expect(screen.getByText("3")).toBeTruthy();
  });

  it("uses updated labels 今日复习 and 今日新学", () => {
    render(<CompletePage stats={normalStats()} onBack={() => {}} />);

    expect(screen.getByText("今日复习")).toBeTruthy();
    expect(screen.getByText("今日新学")).toBeTruthy();
  });

  it("shows dash for learnedToday in CRAM mode (-1)", () => {
    render(
      <CompletePage
        stats={normalStats({ learnedToday: -1, remaining: -1 })}
        onBack={() => {}}
      />
    );

    expect(screen.getByText("-")).toBeTruthy();
  });

  it("does not show next-due element when nextDueAt is null", () => {
    render(
      <CompletePage stats={normalStats({ nextDueAt: null })} onBack={() => {}} />
    );

    expect(screen.queryByTestId("complete-next-due")).toBeNull();
  });

  it("shows minutes when next due is under 60 minutes", () => {
    render(
      <CompletePage
        stats={normalStats({ nextDueAt: makeDate(10 * 60 * 1000) })}
        onBack={() => {}}
      />
    );

    const el = screen.getByTestId("complete-next-due");
    expect(el.textContent).toContain("分钟后到期");
    expect(el.textContent).not.toContain("小时后到期");
  });

  it("shows hours when next due is over 60 minutes", () => {
    render(
      <CompletePage
        stats={normalStats({ nextDueAt: makeDate(3 * 3600 * 1000) })}
        onBack={() => {}}
      />
    );

    const el = screen.getByTestId("complete-next-due");
    expect(el.textContent).toContain("小时后到期");
    expect(el.textContent).not.toContain("分钟后到期");
  });

  it("shows 即将到期 when due time has passed", () => {
    render(
      <CompletePage
        stats={normalStats({ nextDueAt: makeDate(-60 * 1000) })}
        onBack={() => {}}
      />
    );

    expect(screen.getByText("即将到期")).toBeTruthy();
  });

  it("shows seconds when next due is under 60 seconds", () => {
    render(
      <CompletePage
        stats={normalStats({ nextDueAt: makeDate(30 * 1000) })}
        onBack={() => {}}
      />
    );

    const el = screen.getByTestId("complete-next-due");
    expect(el.textContent).toContain("秒后到期");
    expect(el.textContent).not.toContain("分钟后到期");
  });

  it("calls onBack when back button is clicked", () => {
    const onBack = vi.fn();
    render(<CompletePage stats={normalStats()} onBack={onBack} />);

    fireEvent.click(screen.getByTestId("complete-back-btn"));

    expect(onBack).toHaveBeenCalledOnce();
  });
});
