import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import React from "react";
import { FlashcardPanel } from "../../components/chat/FlashcardPanel/FlashcardPanel";

const TAGS_FIXTURE = [
  { id: "1", name: "vocabulary", type: "deck" },
  { id: "2", name: "grammar", type: "topic" },
];

describe("FlashcardPanel", () => {
  beforeEach(() => {
    globalThis.fetch = vi.fn().mockResolvedValue({
      json: () => Promise.resolve(TAGS_FIXTURE),
    });
  });

  it("shows stage 1 with front input when opened", () => {
    render(React.createElement(FlashcardPanel, { isOpen: true, onToggle: vi.fn() }));
    expect(screen.getByTestId("flashcard-front")).toBeTruthy();
    expect(screen.getByTestId("flashcard-continue")).toBeTruthy();
  });

  it("transitions to stage 2 on continue click", () => {
    render(React.createElement(FlashcardPanel, { isOpen: true, onToggle: vi.fn() }));
    fireEvent.change(screen.getByTestId("flashcard-front"), { target: { value: "yesterday" } });
    fireEvent.click(screen.getByTestId("flashcard-continue"));
    expect(screen.getByTestId("flashcard-back")).toBeTruthy();
    expect(screen.getByTestId("flashcard-save")).toBeTruthy();
  });

  it("shows tag suggestions on input click", async () => {
    render(React.createElement(FlashcardPanel, { isOpen: true, onToggle: vi.fn() }));
    fireEvent.change(screen.getByTestId("flashcard-front"), { target: { value: "test" } });
    fireEvent.click(screen.getByTestId("flashcard-continue"));

    fireEvent.change(screen.getByTestId("inline-chip-input-field"), { target: { value: "v" } });
    await waitFor(() => {
      expect(screen.getByTestId("inline-chip-suggestions")).toBeTruthy();
    });
  });

  it("adds chip on suggestion click", async () => {
    render(React.createElement(FlashcardPanel, { isOpen: true, onToggle: vi.fn() }));
    fireEvent.change(screen.getByTestId("flashcard-front"), { target: { value: "test" } });
    fireEvent.click(screen.getByTestId("flashcard-continue"));

    fireEvent.change(screen.getByTestId("inline-chip-input-field"), { target: { value: "v" } });
    await waitFor(() => {
      expect(screen.getByTestId("inline-chip-suggestions")).toBeTruthy();
    });
    fireEvent.click(screen.getAllByTestId("inline-chip-suggestion")[0]);
    expect(screen.getByTestId("inline-chip")).toBeTruthy();
  });

  it("saves card via POST and shows toast", async () => {
    const onToggle = vi.fn();
    globalThis.fetch = vi.fn().mockImplementation((url: string) => {
      if ((url as string).includes("/api/tags")) {
        return Promise.resolve({ json: () => Promise.resolve(TAGS_FIXTURE) });
      }
      return Promise.resolve({ json: () => Promise.resolve({ id: 1 }), ok: true });
    });

    render(React.createElement(FlashcardPanel, { isOpen: true, onToggle }));
    fireEvent.change(screen.getByTestId("flashcard-front"), { target: { value: "yesterday" } });
    fireEvent.click(screen.getByTestId("flashcard-continue"));
    fireEvent.change(screen.getByTestId("flashcard-back"), { target: { value: "昨天" } });

    fireEvent.change(screen.getByTestId("inline-chip-input-field"), { target: { value: "v" } });
    await waitFor(() => {
      expect(screen.getByTestId("inline-chip-suggestions")).toBeTruthy();
    });
    fireEvent.click(screen.getAllByTestId("inline-chip-suggestion")[0]);

    fireEvent.click(screen.getByTestId("flashcard-save"));

    await waitFor(() => {
      expect(screen.getByTestId("flashcard-toast")).toBeTruthy();
    });
  });

  it("has aria-expanded when panel is open", () => {
    render(React.createElement(FlashcardPanel, { isOpen: true, onToggle: vi.fn() }));
    expect(screen.getByTestId("flashcard-panel").getAttribute("aria-expanded")).toBe("true");
  });

  it("has aria-expanded false when panel is closed", () => {
    render(React.createElement(FlashcardPanel, { isOpen: false, onToggle: vi.fn() }));
    expect(screen.getByTestId("flashcard-panel").getAttribute("aria-expanded")).toBe("false");
  });
});
