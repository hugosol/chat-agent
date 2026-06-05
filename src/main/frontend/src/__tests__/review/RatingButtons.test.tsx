import { describe, it, expect, vi, afterEach } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { RatingButtons } from "../../components/review/RatingButtons";
import type { RatingValue } from "../../components/review/RatingButtons";
import type { PreviewInfo } from "../../components/review/reviewTypes";

describe("RatingButtons", () => {
  it("renders all four rating buttons", () => {
    render(<RatingButtons onRate={() => {}} />);

    expect(screen.getByTestId("rating-again")).toBeTruthy();
    expect(screen.getByTestId("rating-hard")).toBeTruthy();
    expect(screen.getByTestId("rating-good")).toBeTruthy();
    expect(screen.getByTestId("rating-easy")).toBeTruthy();
  });

  it("calls onRate with AGAIN when Again clicked", () => {
    const onRate = vi.fn();
    render(<RatingButtons onRate={onRate} />);

    fireEvent.click(screen.getByTestId("rating-again"));

    expect(onRate).toHaveBeenCalledWith("AGAIN" as RatingValue);
  });

  it("calls onRate with GOOD when Good clicked", () => {
    const onRate = vi.fn();
    render(<RatingButtons onRate={onRate} />);

    fireEvent.click(screen.getByTestId("rating-good"));

    expect(onRate).toHaveBeenCalledWith("GOOD" as RatingValue);
  });

  it("buttons are disabled when disabled prop is true", () => {
    render(<RatingButtons onRate={() => {}} disabled={true} />);

    expect((screen.getByTestId("rating-again") as HTMLButtonElement).disabled).toBe(true);
    expect((screen.getByTestId("rating-hard") as HTMLButtonElement).disabled).toBe(true);
    expect((screen.getByTestId("rating-good") as HTMLButtonElement).disabled).toBe(true);
    expect((screen.getByTestId("rating-easy") as HTMLButtonElement).disabled).toBe(true);
  });

  it("buttons are not disabled by default", () => {
    render(<RatingButtons onRate={() => {}} />);

    expect((screen.getByTestId("rating-again") as HTMLButtonElement).disabled).toBe(false);
  });

  it("shows interval label when preview is provided", () => {
    const now = new Date();
    const preview: PreviewInfo = {
      AGAIN: { stability: 0.212, difficulty: 6.4133, state: 1, step: 0, due: new Date(now.getTime() + 30 * 1000).toISOString(), reps: 1, lapses: 0, lastReview: now.toISOString(), elapsedDays: 0 },
      HARD: { stability: 1.2931, difficulty: 5.1122, state: 1, step: 0, due: new Date(now.getTime() + 10 * 60 * 1000).toISOString(), reps: 1, lapses: 0, lastReview: now.toISOString(), elapsedDays: 0 },
      GOOD: { stability: 2.3065, difficulty: 2.1181, state: 1, step: 1, due: new Date(now.getTime() + 8 * 3600 * 1000).toISOString(), reps: 1, lapses: 0, lastReview: now.toISOString(), elapsedDays: 0 },
      EASY: { stability: 8.2956, difficulty: 1.0, state: 2, step: -1, due: new Date(now.getTime() + 4 * 86400 * 1000).toISOString(), reps: 1, lapses: 0, lastReview: now.toISOString(), elapsedDays: 0 },
    };

    render(<RatingButtons onRate={() => {}} preview={preview} />);

    expect(screen.getByTestId("rating-again").textContent).toContain("Again");
    expect(screen.getByTestId("rating-again").textContent).toContain("<1分钟");
    expect(screen.getByTestId("rating-easy").textContent).toContain("4天");
  });

  it("shows plain label when preview is null", () => {
    render(<RatingButtons onRate={() => {}} preview={null} />);

    expect(screen.getByTestId("rating-again").textContent).toBe("Again");
    expect(screen.getByTestId("rating-hard").textContent).toBe("Hard");
    expect(screen.getByTestId("rating-good").textContent).toBe("Good");
    expect(screen.getByTestId("rating-easy").textContent).toBe("Easy");
  });
});
