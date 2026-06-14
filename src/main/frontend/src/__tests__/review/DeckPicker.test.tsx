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
          json: () => Promise.resolve({
            learnedToday: 5,
            reviewedToday: 8,
            remaining: 12,
            dailyLimit: 20,
            totalNewCards: 50,
          }),
        } as Response);
      }
      return Promise.resolve({ ok: true, json: () => Promise.resolve({}) } as Response);
    });
  });

  it("renders deck select with options including count", async () => {
    render(<DeckPicker onStart={() => {}} />);

    await waitFor(() => {
      expect(screen.getByText("Daily English (45张)")).toBeTruthy();
    });

    expect(screen.getByText("Business (12张)")).toBeTruthy();
    expect(screen.getByText("请选择牌组")).toBeTruthy();
  });

  it("renders mode select with description", async () => {
    render(<DeckPicker onStart={() => {}} />);

    await waitFor(() => {
      expect(screen.getByText("标准模式")).toBeTruthy();
    });

    expect(screen.getByText("仅复习")).toBeTruthy();
    expect(screen.getByText("仅新卡")).toBeTruthy();
    expect(screen.getByText("速通")).toBeTruthy();
    expect(screen.getByText("新卡 + 到期卡，按到期时间排序")).toBeTruthy();
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

    fireEvent.change(screen.getByTestId("mode-select"), {
      target: { value: "REVIEW_ONLY" },
    });

    await waitFor(() => {
      expect(screen.queryByTestId("limit-section")).toBeNull();
    });
  });

  it("updates mode description when mode changes", async () => {
    render(<DeckPicker onStart={() => {}} />);

    await waitFor(() => {
      expect(screen.getByText("新卡 + 到期卡，按到期时间排序")).toBeTruthy();
    });

    fireEvent.change(screen.getByTestId("mode-select"), {
      target: { value: "REVIEW_ONLY" },
    });

    await waitFor(() => {
      expect(screen.getByText("仅复习已学到期卡片")).toBeTruthy();
    });
  });

  it("start button disabled when no deck selected", async () => {
    render(<DeckPicker onStart={() => {}} />);

    await waitFor(() => {
      const btn = screen.getByTestId("start-btn") as HTMLButtonElement;
      expect(btn.disabled).toBe(true);
    });
  });

  it("start button enabled when deck selected", async () => {
    render(<DeckPicker onStart={() => {}} />);

    await waitFor(() => {
      expect(screen.getByText("Daily English (45张)")).toBeTruthy();
    });

    fireEvent.change(screen.getByTestId("deck-select"), {
      target: { value: "deck-1" },
    });

    await waitFor(() => {
      const btn = screen.getByTestId("start-btn") as HTMLButtonElement;
      expect(btn.disabled).toBe(false);
    });
  });

  it("renders start button with correct text", async () => {
    render(<DeckPicker onStart={() => {}} />);

    await waitFor(() => {
      const btn = screen.getByTestId("start-btn");
      expect(btn.textContent).toBe("开始练习");
    });
  });

  it("shows remaining count when deck and mode are selected", async () => {
    render(<DeckPicker onStart={() => {}} />);

    await waitFor(() => {
      expect(screen.getByText("Daily English (45张)")).toBeTruthy();
    });

    fireEvent.change(screen.getByTestId("deck-select"), {
      target: { value: "deck-1" },
    });

    await waitFor(() => {
      const el = screen.getByTestId("mode-remaining");
      expect(el.textContent).toContain("剩余 12 张");
    });
  });

  it("does not show remaining when no deck selected", async () => {
    render(<DeckPicker onStart={() => {}} />);

    await waitFor(() => {
      expect(screen.getByText("标准模式")).toBeTruthy();
    });

    expect(screen.queryByTestId("mode-remaining")).toBeNull();
  });

  it("shows estimated days to finish new cards", async () => {
    render(<DeckPicker onStart={() => {}} />);

    await waitFor(() => {
      expect(screen.getByText("请选择牌组")).toBeTruthy();
    });

    fireEvent.change(screen.getByTestId("deck-select"), {
      target: { value: "deck-1" },
    });

    await waitFor(() => {
      const el = screen.getByText(/预计还需/);
      expect(el.textContent).toContain("预计还需 10 天学完");
    });
  });
});
