import { describe, it, expect, vi, beforeEach } from "vitest";
import { speakText } from "../../shared/tts";

class MockSpeechSynthesisUtterance {
  text: string;
  lang = '';
  rate = 1;
  constructor(text: string) {
    this.text = text;
  }
}

describe("speakText", () => {
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
      MockSpeechSynthesisUtterance;
  });

  it("cancels previous speech, creates utterance with correct params, and speaks", () => {
    speakText("Hello");

    expect(window.speechSynthesis.cancel).toHaveBeenCalledOnce();

    expect(window.speechSynthesis.speak).toHaveBeenCalledOnce();
    const utterance = (window.speechSynthesis.speak as ReturnType<typeof vi.fn>).mock
      .calls[0][0] as MockSpeechSynthesisUtterance;
    expect(utterance.text).toBe("Hello");
    expect(utterance.lang).toBe("en-US");
    expect(utterance.rate).toBe(0.95);
  });

  it("sets lang to ja-JP when mode is JAPANESE_BUSINESS", () => {
    speakText("こんにちは", "JAPANESE_BUSINESS");

    const utterance = (window.speechSynthesis.speak as ReturnType<typeof vi.fn>).mock
      .calls[0][0] as MockSpeechSynthesisUtterance;
    expect(utterance.text).toBe("こんにちは");
    expect(utterance.lang).toBe("ja-JP");
  });
});
