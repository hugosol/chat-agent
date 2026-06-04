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

  it("shows TTS button for English front after flip", () => {
    render(<CardDisplay front="hello" back="你好" />);

    fireEvent.click(screen.getByTestId("flip-card-btn"));

    expect(screen.getByTestId("tts-btn-front")).toBeTruthy();
  });

  it("does not show TTS button for non-English front", () => {
    render(<CardDisplay front="你好" back="hello" />);

    fireEvent.click(screen.getByTestId("flip-card-btn"));

    expect(screen.queryByTestId("tts-btn-front")).toBeNull();
    expect(screen.getByTestId("tts-btn-back")).toBeTruthy();
  });
});
