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
    (globalThis as Record<string, unknown>).SpeechSynthesisUtterance =
      class MockSpeechSynthesisUtterance {
        text = "";
        lang = "";
        rate = 1;
        constructor(text: string) { this.text = text; }
      };
    vi.restoreAllMocks();
    // Clean up any lingering toast elements from previous tests
    document.querySelectorAll('[data-testid="toast"]').forEach(el => el.remove());
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

  it("shows magnifier icon when flipped and no enhancement", () => {
    render(<CardDisplay front="hello" back="你好" flipped={true} onFlip={vi.fn()} />);

    expect(screen.getByTestId("card-enhance-btn")).toBeTruthy();
    expect(screen.getByTestId("card-enhance-btn").textContent).toBe("\uD83D\uDD0D");
  });

  it("does not show magnifier when enhancement exists", () => {
    const enhancement = {
      movieQuote: { movieTitle: "Inception", imdbId: "tt001", quote: "dream", timestamp: "00:05:00" },
      sceneSummary: "梦境场景。",
      etymology: "From Old English.",
    };
    render(<CardDisplay front="hello" back="你好" flipped={true} enhancement={enhancement} onFlip={vi.fn()} />);

    expect(screen.queryByTestId("card-enhance-btn")).toBeNull();
  });

  it("shows confirm dialog when magnifier is clicked", () => {
    render(<CardDisplay front="hello" back="你好" flipped={true} onFlip={vi.fn()} />);

    fireEvent.click(screen.getByTestId("card-enhance-btn"));

    expect(screen.getByTestId("enhance-confirm-dialog")).toBeTruthy();
    expect(screen.getByText("是否获取更多信息？")).toBeTruthy();
  });

  it("does not call API when confirm dialog is cancelled", () => {
    const mockFetch = vi.fn();
    vi.stubGlobal("fetch", mockFetch);

    render(<CardDisplay front="hello" back="你好" flipped={true} onFlip={vi.fn()} />);

    fireEvent.click(screen.getByTestId("card-enhance-btn"));
    fireEvent.click(screen.getByTestId("enhance-confirm-cancel"));

    expect(mockFetch).not.toHaveBeenCalled();
    expect(screen.getByTestId("card-enhance-btn")).toBeTruthy();
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

  it("shows movie quote placeholder when enhancement exists but movieQuote is null", () => {
    const enhancement = {
      etymology: "From Old English.",
    };
    render(<CardDisplay front="hello" back="你好" flipped={true} enhancement={enhancement} onFlip={vi.fn()} />);

    expect(screen.getByTestId("movie-quote-zone")).toBeTruthy();
    expect(screen.getByTestId("movie-quote-placeholder")).toBeTruthy();
    expect(screen.getByText("【暂无电影台词数据】")).toBeTruthy();
  });

  it("shows etymology placeholder when enhancement exists but etymology is null", () => {
    const enhancement = {
      movieQuote: { movieTitle: "Inception", imdbId: "tt001", quote: "dream", timestamp: "00:05:00" },
    };
    render(<CardDisplay front="hello" back="你好" flipped={true} enhancement={enhancement} onFlip={vi.fn()} />);

    expect(screen.getByTestId("etymology-zone")).toBeTruthy();
    expect(screen.getByTestId("etymology-placeholder")).toBeTruthy();
    expect(screen.getByText("【暂无词源数据】")).toBeTruthy();
  });

  it("does not show enhance button when enhancement is non-null but empty", () => {
    render(<CardDisplay front="hello" back="你好" flipped={true} enhancement={{}} onFlip={vi.fn()} />);

    expect(screen.queryByTestId("card-enhance-btn")).toBeNull();
    expect(screen.getByTestId("movie-quote-placeholder")).toBeTruthy();
    expect(screen.getByTestId("etymology-placeholder")).toBeTruthy();
  });

  it("card back is scrollable when enhancement exists (even without quote data)", () => {
    // enhancement != null means records exist, back should be scrollable for enhanced layout
    render(<CardDisplay front="hello" back="你好" flipped={true} enhancement={{}} onFlip={vi.fn()} />);

    const backEl = screen.getByTestId("card-back");
    expect(backEl.classList.contains("scrollableBack")).toBe(true);
  });

  it("calls enhance API when confirm dialog is confirmed", async () => {
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
    fireEvent.click(screen.getByTestId("enhance-confirm-ok"));

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
    fireEvent.click(screen.getByTestId("enhance-confirm-ok"));

    expect(screen.getByTestId("enhance-loading")).toBeTruthy();
  });

  it("hides magnifier during enhancement loading", () => {
    const mockFetch = vi.fn().mockImplementation(() => new Promise(() => {}));
    vi.stubGlobal("fetch", mockFetch);

    render(<CardDisplay front="hello" back="你好" cardId="card-1" flipped={true} onFlip={vi.fn()} />);

    fireEvent.click(screen.getByTestId("card-enhance-btn"));
    fireEvent.click(screen.getByTestId("enhance-confirm-ok"));

    expect(screen.queryByTestId("card-enhance-btn")).toBeNull();
  });

  it("shows magnifier again when enhance API returns empty data (both null)", async () => {
    const mockFetch = vi.fn().mockResolvedValue({
      ok: true,
      json: () => Promise.resolve({ sceneSummary: null, etymology: null }),
    });
    vi.stubGlobal("fetch", mockFetch);

    render(<CardDisplay front="hello" back="你好" cardId="card-1" flipped={true} onFlip={vi.fn()} />);

    fireEvent.click(screen.getByTestId("card-enhance-btn"));
    fireEvent.click(screen.getByTestId("enhance-confirm-ok"));

    await waitFor(() => {
      expect(screen.getByTestId("card-enhance-btn")).toBeTruthy();
    });
  });

  it("shows error persistently when enhance API fails", async () => {
    const mockFetch = vi.fn().mockResolvedValue({
      ok: false,
      status: 500,
    });
    vi.stubGlobal("fetch", mockFetch);

    render(<CardDisplay front="hello" back="你好" cardId="card-1" flipped={true} onFlip={vi.fn()} />);

    fireEvent.click(screen.getByTestId("card-enhance-btn"));
    fireEvent.click(screen.getByTestId("enhance-confirm-ok"));

    await waitFor(() => {
      expect(screen.getByTestId("enhance-error")).toBeTruthy();
    });
    // Magnifier should still be visible for retry
    expect(screen.getByTestId("card-enhance-btn")).toBeTruthy();
  });

  // ── Quote TTS + Replace button tests ──

  it("shows TTS and Replace buttons in movie quote zone when movieQuote exists", () => {
    const enhancement = {
      movieQuote: { movieTitle: "Inception", imdbId: "tt001", quote: "dream bigger", timestamp: "00:05:00" },
      sceneSummary: "梦境场景。",
    };
    render(<CardDisplay front="hello" back="你好" flipped={true} enhancement={enhancement} onFlip={vi.fn()} />);

    expect(screen.getByTestId("tts-btn-quote")).toBeTruthy();
    expect(screen.getByTestId("requote-btn")).toBeTruthy();
  });

  it("TTS quote button calls speakText with quote text", () => {
    const enhancement = {
      movieQuote: { movieTitle: "Inception", imdbId: "tt001", quote: "dream bigger", timestamp: "00:05:00" },
    };
    render(<CardDisplay front="hello" back="你好" flipped={true} enhancement={enhancement} onFlip={vi.fn()} />);

    fireEvent.click(screen.getByTestId("tts-btn-quote"));

    expect(window.speechSynthesis.speak).toHaveBeenCalled();
    const utterance = (window.speechSynthesis.speak as ReturnType<typeof vi.fn>).mock.calls[0][0] as SpeechSynthesisUtterance;
    expect(utterance.text).toBe("dream bigger");
  });

  it("shows Replace button in movie quote zone when movieQuote is null", () => {
    const enhancement = { etymology: "From Old English." };
    render(<CardDisplay front="hello" back="你好" flipped={true} enhancement={enhancement} onFlip={vi.fn()} />);

    expect(screen.getByTestId("movie-quote-placeholder")).toBeTruthy();
    expect(screen.getByTestId("requote-btn")).toBeTruthy();
  });

  it("shows Replace confirm dialog when requote button is clicked", () => {
    const enhancement = {
      movieQuote: { movieTitle: "Inception", imdbId: "tt001", quote: "dream", timestamp: "00:05:00" },
    };
    render(<CardDisplay front="hello" back="你好" flipped={true} enhancement={enhancement} onFlip={vi.fn()} />);

    fireEvent.click(screen.getByTestId("requote-btn"));

    expect(screen.getByTestId("requote-confirm-dialog")).toBeTruthy();
    expect(screen.getByText("替换为另一句电影台词？")).toBeTruthy();
  });

  it("shows search confirm dialog when requote clicked with no current quote", () => {
    const enhancement = { etymology: "From Old English." };
    render(<CardDisplay front="hello" back="你好" flipped={true} enhancement={enhancement} onFlip={vi.fn()} />);

    fireEvent.click(screen.getByTestId("requote-btn"));

    expect(screen.getByText("搜索电影台词？")).toBeTruthy();
  });

  it("does not call requote API when confirm dialog is cancelled", () => {
    const mockFetch = vi.fn();
    vi.stubGlobal("fetch", mockFetch);

    const enhancement = {
      movieQuote: { movieTitle: "Inception", imdbId: "tt001", quote: "dream", timestamp: "00:05:00" },
    };
    render(<CardDisplay front="hello" back="你好" flipped={true} enhancement={enhancement} onFlip={vi.fn()} />);

    fireEvent.click(screen.getByTestId("requote-btn"));
    fireEvent.click(screen.getByTestId("requote-confirm-cancel"));

    expect(mockFetch).not.toHaveBeenCalled();
  });

  it("calls requote API with imdbId+timestamp when confirmed", async () => {
    const mockFetch = vi.fn().mockResolvedValue({
      ok: true,
      json: () => Promise.resolve({
        found: true,
        movieQuote: { movieTitle: "The Matrix", imdbId: "tt002", quote: "There is no spoon.", timestamp: "00:10:00" },
        sceneSummary: "Matrix scene.",
      }),
    });
    vi.stubGlobal("fetch", mockFetch);

    const enhancement = {
      movieQuote: { movieTitle: "Inception", imdbId: "tt001", quote: "dream", timestamp: "00:05:00" },
    };
    render(<CardDisplay front="hello" back="你好" cardId="card-1" flipped={true} enhancement={enhancement} onFlip={vi.fn()} />);

    fireEvent.click(screen.getByTestId("requote-btn"));
    fireEvent.click(screen.getByTestId("requote-confirm-ok"));

    expect(mockFetch).toHaveBeenCalledWith("/api/cards/card-1/enhance/requote", expect.objectContaining({
      method: "POST",
      body: JSON.stringify({ imdbId: "tt001", timestamp: "00:05:00" }),
    }));

    await waitFor(() => {
      expect(screen.getByTestId("movie-quote-zone").textContent).toContain("The Matrix");
      expect(screen.getByText(/There is no spoon/)).toBeTruthy();
    });
  });

  // ── Requote loading/error state tests ──

  it("shows loading overlay during requote", () => {
    const mockFetch = vi.fn().mockImplementation(() => new Promise(() => {}));
    vi.stubGlobal("fetch", mockFetch);

    const enhancement = {
      movieQuote: { movieTitle: "Inception", imdbId: "tt001", quote: "dream", timestamp: "00:05:00" },
    };
    render(<CardDisplay front="hello" back="你好" cardId="card-1" flipped={true} enhancement={enhancement} onFlip={vi.fn()} />);

    fireEvent.click(screen.getByTestId("requote-btn"));
    fireEvent.click(screen.getByTestId("requote-confirm-ok"));

    expect(screen.getByTestId("requote-loading")).toBeTruthy();
  });

  it("shows toast when requote API fails", async () => {
    const mockFetch = vi.fn().mockResolvedValue({ ok: false, status: 500 });
    vi.stubGlobal("fetch", mockFetch);

    const enhancement = {
      movieQuote: { movieTitle: "Inception", imdbId: "tt001", quote: "dream", timestamp: "00:05:00" },
    };
    render(<CardDisplay front="hello" back="你好" cardId="card-1" flipped={true} enhancement={enhancement} onFlip={vi.fn()} />);

    fireEvent.click(screen.getByTestId("requote-btn"));
    fireEvent.click(screen.getByTestId("requote-confirm-ok"));

    await waitFor(() => {
      expect(screen.getByTestId("toast")).toBeTruthy();
      expect(screen.getByText("替换失败，请重试")).toBeTruthy();
    });
  });

  it("shows toast with reason when requote returns found:false with reason", async () => {
    const mockFetch = vi.fn().mockResolvedValue({
      ok: true,
      json: () => Promise.resolve({ found: false, reason: "no_subtitle_match" }),
    });
    vi.stubGlobal("fetch", mockFetch);

    const enhancement = {
      movieQuote: { movieTitle: "Inception", imdbId: "tt001", quote: "dream", timestamp: "00:05:00" },
    };
    render(<CardDisplay front="hello" back="你好" cardId="card-1" flipped={true} enhancement={enhancement} onFlip={vi.fn()} />);

    fireEvent.click(screen.getByTestId("requote-btn"));
    fireEvent.click(screen.getByTestId("requote-confirm-ok"));

    await waitFor(() => {
      expect(screen.getByTestId("toast")).toBeTruthy();
      expect(screen.getByText("你的电影库中没有包含该词的对白")).toBeTruthy();
    });
  });

  it("shows generic toast when requote returns found:false without reason", async () => {
    const mockFetch = vi.fn().mockResolvedValue({
      ok: true,
      json: () => Promise.resolve({ found: false }),
    });
    vi.stubGlobal("fetch", mockFetch);

    const enhancement = {
      movieQuote: { movieTitle: "Inception", imdbId: "tt001", quote: "dream", timestamp: "00:05:00" },
    };
    render(<CardDisplay front="hello" back="你好" cardId="card-1" flipped={true} enhancement={enhancement} onFlip={vi.fn()} />);

    fireEvent.click(screen.getByTestId("requote-btn"));
    fireEvent.click(screen.getByTestId("requote-confirm-ok"));

    await waitFor(() => {
      expect(screen.getByTestId("toast")).toBeTruthy();
      expect(screen.getByText("没有其他可替换的台词")).toBeTruthy();
    });
  });

  it("shows success toast when requote returns a new match", async () => {
    const mockFetch = vi.fn().mockResolvedValue({
      ok: true,
      json: () => Promise.resolve({
        found: true,
        movieQuote: { movieTitle: "The Matrix", imdbId: "tt002", quote: "There is no spoon.", timestamp: "00:10:00" },
        sceneSummary: "Matrix scene.",
      }),
    });
    vi.stubGlobal("fetch", mockFetch);

    const enhancement = {
      movieQuote: { movieTitle: "Inception", imdbId: "tt001", quote: "dream", timestamp: "00:05:00" },
    };
    render(<CardDisplay front="hello" back="你好" cardId="card-1" flipped={true} enhancement={enhancement} onFlip={vi.fn()} />);

    fireEvent.click(screen.getByTestId("requote-btn"));
    fireEvent.click(screen.getByTestId("requote-confirm-ok"));

    await waitFor(() => {
      expect(screen.getByTestId("toast")).toBeTruthy();
      expect(screen.getByText(/已替换为《The Matrix》的台词/)).toBeTruthy();
    });
  });
});
