import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { CardDisplay } from "../../components/review/CardDisplay";

describe("CardDisplay", () => {
  beforeEach(() => {
    const mockSpeechSynthesis = {
      speak: vi.fn(),
      cancel: vi.fn(),
    };
    Object.defineProperty(window, "speechSynthesis", {
      value: mockSpeechSynthesis,
      writable: true,
    });
  });

  it("renders front text initially", () => {
    render(<CardDisplay front="hello" back="你好" />);

    expect(screen.getByTestId("card-front")).toBeTruthy();
    expect(screen.getByText("hello")).toBeTruthy();
  });

  it("does not show back initially", () => {
    render(<CardDisplay front="hello" back="你好" />);

    expect(screen.queryByTestId("card-back")).toBeNull();
  });

  it("shows back after click", () => {
    render(<CardDisplay front="hello" back="你好" />);

    fireEvent.click(screen.getByTestId("flip-card-btn"));

    expect(screen.getByTestId("card-back")).toBeTruthy();
    expect(screen.getByText("你好")).toBeTruthy();
  });

  it("shows TTS button for English front before flip", () => {
    render(<CardDisplay front="hello" back="你好" />);

    expect(screen.getByTestId("tts-btn-front")).toBeTruthy();
  });

  it("does not show TTS button for non-English front", () => {
    render(<CardDisplay front="你好" back="hello" />);

    expect(screen.queryByTestId("tts-btn-front")).toBeNull();
  });

  it("renders back text with newlines as line breaks", () => {
    render(<CardDisplay front="hello" back={"line1\nline2\nline3"} />);

    fireEvent.click(screen.getByTestId("flip-card-btn"));

    const backEl = screen.getByTestId("card-back");
    expect(backEl.textContent).toContain("line1");
    expect(backEl.textContent).toContain("line2");
    expect(backEl.textContent).toContain("line3");
    expect(backEl.querySelectorAll("br").length).toBe(2);
  });
});
