import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { StatsBar } from "../../components/review/StatsBar";
import type { ReviewStats } from "../../components/review/reviewTypes";

function stats(overrides: Partial<ReviewStats> = {}): ReviewStats {
  return {
    reviewedToday: 5,
    remaining: 10,
    learnedToday: 2,
    dailyLimit: 20,
    nextDueAt: null,
    ...overrides,
  };
}

describe("StatsBar", () => {
  it("shows 今日：已复习 label with count", () => {
    render(<StatsBar stats={stats()} />);

    expect(screen.getByText("今日：已复习 5 张")).toBeTruthy();
  });

  it("shows dash for remaining and new cards in CRAM mode", () => {
    render(<StatsBar stats={stats({ remaining: -1, learnedToday: -1 })} />);

    const remainingEl = screen.getByTestId("stats-remaining");
    expect(remainingEl.textContent).toContain("剩余 - 张");

    const newEl = screen.getByTestId("stats-new");
    expect(newEl.textContent).toContain("-/-");
  });
});
