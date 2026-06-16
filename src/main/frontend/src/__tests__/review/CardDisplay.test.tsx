import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
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
    vi.restoreAllMocks();
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

  // Enhancement tests

  it("shows Card Enhance button when flipped and no enhancement", () => {
    render(<CardDisplay front="hello" back="你好" flipped={true} onFlip={vi.fn()} />);

    expect(screen.getByTestId("card-enhance-btn")).toBeTruthy();
  });

  it("does not show Card Enhance button when enhancement data is present", () => {
    const enhancement = {
      movieQuote: { movieTitle: "Inception", imdbId: "tt001", quote: "dream", timestamp: "00:05:00" },
      sceneSummary: "梦境场景。",
      etymology: "From Old English.",
    };
    render(<CardDisplay front="hello" back="你好" flipped={true} enhancement={enhancement} onFlip={vi.fn()} />);

    expect(screen.queryByTestId("card-enhance-btn")).toBeNull();
  });

  it("shows movie quote zone when enhancement has movieQuote", () => {
    const enhancement = {
      movieQuote: { movieTitle: "Inception", imdbId: "tt001", quote: "dream bigger", timestamp: "00:05:00" },
      sceneSummary: "梦境场景。",
    };
    render(<CardDisplay front="hello" back="你好" flipped={true} enhancement={enhancement} onFlip={vi.fn()} />);

    expect(screen.getByTestId("movie-quote-zone")).toBeTruthy();
    expect(screen.getByText(/Inception/)).toBeTruthy();
    expect(screen.getByText(/dream bigger/)).toBeTruthy();
    expect(screen.getByTestId("scene-summary")).toBeTruthy();
  });

  it("shows etymology zone when enhancement has etymology", () => {
    const enhancement = {
      etymology: "From Old English drēam.",
    };
    render(<CardDisplay front="hello" back="你好" flipped={true} enhancement={enhancement} onFlip={vi.fn()} />);

    expect(screen.getByTestId("etymology-zone")).toBeTruthy();
    expect(screen.getByText("From Old English drēam.")).toBeTruthy();
  });

  it("does not show movie quote zone when movieQuote is null", () => {
    const enhancement = {
      etymology: "From Old English.",
    };
    render(<CardDisplay front="hello" back="你好" flipped={true} enhancement={enhancement} onFlip={vi.fn()} />);

    expect(screen.queryByTestId("movie-quote-zone")).toBeNull();
  });

  it("card back is scrollable when enhancement present", () => {
    const enhancement = {
      movieQuote: { movieTitle: "Inception", imdbId: "tt001", quote: "dream", timestamp: "00:05:00" },
    };
    render(<CardDisplay front="hello" back="你好" flipped={true} enhancement={enhancement} onFlip={vi.fn()} />);

    const backEl = screen.getByTestId("card-back");
    expect(backEl.classList.contains("scrollableBack")).toBe(true);
  });

  it("calls enhance API when button clicked", async () => {
    const mockFetch = vi.fn().mockResolvedValue({
      ok: true,
      json: () => Promise.resolve({
        movieQuote: { movieTitle: "Test", imdbId: "tt001", quote: "word", timestamp: "00:01:00" },
        sceneSummary: "summary",
        etymology: "etym",
      }),
    });
    vi.stubGlobal("fetch", mockFetch);

    render(<CardDisplay front="hello" back="你好" cardId="card-1" flipped={true} onFlip={vi.fn()} />);

    fireEvent.click(screen.getByTestId("card-enhance-btn"));

    expect(mockFetch).toHaveBeenCalledWith("/api/cards/card-1/enhance", expect.objectContaining({
      method: "POST",
    }));

    await waitFor(() => {
      expect(screen.getByTestId("movie-quote-zone")).toBeTruthy();
    });
  });

  it("shows loading overlay during enhancement", () => {
    const mockFetch = vi.fn().mockImplementation(() => new Promise(() => {})); // never resolves
    vi.stubGlobal("fetch", mockFetch);

    render(<CardDisplay front="hello" back="你好" cardId="card-1" flipped={true} onFlip={vi.fn()} />);

    fireEvent.click(screen.getByTestId("card-enhance-btn"));

    expect(screen.getByTestId("enhance-loading")).toBeTruthy();
  });
});
