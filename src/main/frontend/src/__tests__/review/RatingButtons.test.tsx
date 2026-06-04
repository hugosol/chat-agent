import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { RatingButtons } from "../../components/review/RatingButtons";
import type { RatingValue } from "../../components/review/RatingButtons";

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
});
