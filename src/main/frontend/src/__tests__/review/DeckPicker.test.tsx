import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { DeckPicker } from "../../components/review/DeckPicker";

const mockDecks = [
  { id: "deck-1", name: "Daily English", type: "deck", cardCount: 45 },
  { id: "deck-2", name: "Business", type: "deck", cardCount: 12 },
];

describe("DeckPicker", () => {
  beforeEach(() => {
    (global as unknown as { fetch: typeof fetch }).fetch = vi.fn((url: string) => {
      if (url === "/api/review/decks") {
        return Promise.resolve({
          ok: true,
          json: () => Promise.resolve(mockDecks),
        } as Response);
      }
      if (url === "/api/user/preferences") {
        return Promise.resolve({
          ok: true,
          json: () => Promise.resolve({}),
        } as Response);
      }
      if (url.includes("/api/review/stats")) {
        return Promise.resolve({
          ok: true,
          json: () => Promise.resolve({ learnedToday: 0 }),
        } as Response);
      }
      return Promise.resolve({ ok: true, json: () => Promise.resolve({}) } as Response);
    });
  });

  it("renders deck list after loading", async () => {
    render(<DeckPicker onStart={() => {}} />);

    await waitFor(() => {
      expect(screen.getByText("Daily English")).toBeTruthy();
    });

    expect(screen.getByText("Business")).toBeTruthy();
    expect(screen.getByText("45 张")).toBeTruthy();
    expect(screen.getByText("12 张")).toBeTruthy();
  });

  it("renders mode radio buttons", async () => {
    render(<DeckPicker onStart={() => {}} />);

    await waitFor(() => {
      expect(screen.getByText("标准复习")).toBeTruthy();
    });

    expect(screen.getByText("仅复习")).toBeTruthy();
    expect(screen.getByText("仅新卡")).toBeTruthy();
    expect(screen.getByText("速通")).toBeTruthy();
  });

  it("shows limit input for STANDARD mode", async () => {
    render(<DeckPicker onStart={() => {}} />);

    await waitFor(() => {
      const limitSection = screen.queryByTestId("limit-section");
      expect(limitSection).toBeTruthy();
    });
  });

  it("hides limit input for REVIEW_ONLY mode", async () => {
    render(<DeckPicker onStart={() => {}} />);

    await waitFor(() => {
      expect(screen.getByText("仅复习")).toBeTruthy();
    });

    const reviewOnlyRadio = screen.getAllByTestId("mode-item").find(
      (el) => el.textContent?.includes("仅复习")
    );
    if (reviewOnlyRadio) {
      fireEvent.click(reviewOnlyRadio);
    }

    await waitFor(() => {
      expect(screen.queryByTestId("limit-section")).toBeNull();
    });
  });

  it("start button disabled when no deck selected", async () => {
    render(<DeckPicker onStart={() => {}} />);

    await waitFor(() => {
      const btn = screen.getByTestId("start-btn") as HTMLButtonElement;
      expect(btn.disabled).toBe(true);
    });
  });

  it("start button enabled when deck and mode selected", async () => {
    render(<DeckPicker onStart={() => {}} />);

    await waitFor(() => {
      expect(screen.getByText("Daily English")).toBeTruthy();
    });

    const deckItem = screen.getAllByTestId("deck-item")[0];
    fireEvent.click(deckItem);

    await waitFor(() => {
      const btn = screen.getByTestId("start-btn") as HTMLButtonElement;
      expect(btn.disabled).toBe(false);
    });
  });
});
