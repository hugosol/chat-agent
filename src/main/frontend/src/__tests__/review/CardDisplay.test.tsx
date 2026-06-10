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

  it("renders front text when not flipped", () => {
    render(<CardDisplay front="hello" back="你好" flipped={false} onFlip={vi.fn()} />);

    expect(screen.getByTestId("card-front")).toBeTruthy();
    expect(screen.getByText("hello")).toBeTruthy();
  });

  it("does not show back when not flipped", () => {
    render(<CardDisplay front="hello" back="你好" flipped={false} onFlip={vi.fn()} />);

    expect(screen.queryByTestId("card-back")).toBeNull();
  });

  it("calls onFlip when card is clicked", () => {
    const onFlip = vi.fn();
    render(<CardDisplay front="hello" back="你好" flipped={false} onFlip={onFlip} />);

    fireEvent.click(screen.getByTestId("flip-card-btn"));

    expect(onFlip).toHaveBeenCalledTimes(1);
  });

  it("shows back when flipped is true", () => {
    render(<CardDisplay front="hello" back="你好" flipped={true} onFlip={vi.fn()} />);

    expect(screen.getByTestId("card-back")).toBeTruthy();
    expect(screen.getByText("你好")).toBeTruthy();
  });

  it("shows below-card TTS button for English front when not flipped", () => {
    render(<CardDisplay front="hello" back="你好" flipped={false} onFlip={vi.fn()} />);

    expect(screen.getByTestId("tts-btn-below")).toBeTruthy();
    expect(screen.queryByTestId("tts-btn-front")).toBeNull();
  });

  it("shows inline TTS button for English front when flipped", () => {
    render(<CardDisplay front="hello" back="你好" flipped={true} onFlip={vi.fn()} />);

    expect(screen.getByTestId("tts-btn-front")).toBeTruthy();
    expect(screen.queryByTestId("tts-btn-below")).toBeNull();
  });

  it("does not show any TTS button for non-English front when not flipped", () => {
    render(<CardDisplay front="你好" back="hello" flipped={false} onFlip={vi.fn()} />);

    expect(screen.queryByTestId("tts-btn-front")).toBeNull();
    expect(screen.queryByTestId("tts-btn-below")).toBeNull();
  });

  it("renders back text with newlines as line breaks when flipped", () => {
    render(<CardDisplay front="hello" back={"line1\nline2\nline3"} flipped={true} onFlip={vi.fn()} />);

    const backEl = screen.getByTestId("card-back");
    expect(backEl.textContent).toContain("line1");
    expect(backEl.textContent).toContain("line2");
    expect(backEl.textContent).toContain("line3");
    expect(backEl.querySelectorAll("br").length).toBe(2);
  });
});
