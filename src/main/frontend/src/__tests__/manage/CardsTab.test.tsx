import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, fireEvent, screen, waitFor } from "@testing-library/react";
import { CardsTab } from "../../components/manage/CardsTab";
import type { Card, Tag, PageResponse } from "../../shared/types";

const mockCards: Card[] = [
  {
    id: "c1", front: "hello", back: "你好\nexplanation", tags: [],
    due: "2025-06-15T00:00:00", cardState: 0, step: -1, reps: 0, lapses: 0, createTime: "2025-01-01T00:00:00",
  },
];

const mockDecks: Tag[] = [
  { id: "1", name: "Daily", type: "deck" },
];

const mockAllTags: Tag[] = [
  { id: "1", name: "Daily", type: "deck" },
  { id: "2", name: "verb", type: null },
];

function mockCardResponse(cards: Card[], totalPages = 1): PageResponse<Card> {
  return { content: cards, totalPages, totalElements: cards.length, number: 0, size: 10 };
}

function setupFetchMocks(options?: { emptyCards?: boolean; emptyDecks?: boolean }) {
  let callCount = 0;
  const cards = options?.emptyCards ? [] : mockCards;
  const decks = options?.emptyDecks ? [] : mockDecks;
  (global as any).fetch = vi.fn((url: string) => {
    const urlStr = String(url);
    if (urlStr.includes("/api/cards")) {
      return Promise.resolve({
        ok: true,
        json: () => Promise.resolve(mockCardResponse(cards)),
      });
    }
    if (urlStr.includes("/api/tags?type=deck")) {
      return Promise.resolve({
        ok: true,
        json: () => Promise.resolve(decks),
      });
    }
    if (urlStr === "/api/tags") {
      callCount++;
      return Promise.resolve({
        ok: true,
        json: () => Promise.resolve(callCount === 1 ? mockAllTags : decks),
      });
    }
    return Promise.resolve({ ok: true, json: () => Promise.resolve([]) });
  });
}

describe("CardsTab", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    setupFetchMocks();
  });

  it("loads and displays cards on mount", async () => {
    render(<CardsTab />);
    await waitFor(() => {
      expect(screen.getByTestId("card-block")).toBeInTheDocument();
    });
    expect(screen.getByTestId("card-front")).toHaveTextContent("hello");
  });

  it("loads and displays deck tabs", async () => {
    render(<CardsTab />);
    await waitFor(() => {
      const tabs = screen.getAllByTestId("deck-tab");
      expect(tabs.length).toBeGreaterThanOrEqual(1);
    });
    const tabs = screen.getAllByTestId("deck-tab");
    expect(tabs[1]).toHaveTextContent("Daily");
  });

  it("shows empty state when no cards exist", async () => {
    setupFetchMocks({ emptyCards: true });
    render(<CardsTab />);
    await waitFor(() => {
      expect(screen.getByTestId("empty-state")).toBeInTheDocument();
    });
    expect(screen.getByTestId("empty-state")).toHaveTextContent("暂无卡片，点击 + 创建");
  });

  it("shows no-decks hint when decks are empty and no cards", async () => {
    setupFetchMocks({ emptyCards: true, emptyDecks: true });
    render(<CardsTab />);
    await waitFor(() => {
      expect(screen.getByTestId("empty-state")).toBeInTheDocument();
    });
    expect(screen.getByTestId("empty-state")).toHaveTextContent("暂无牌组，请先在 Tags 页面创建牌组");
  });

  it("opens detail modal when a card block is clicked", async () => {
    render(<CardsTab />);
    await waitFor(() => {
      expect(screen.getByTestId("card-block")).toBeInTheDocument();
    });
    fireEvent.click(screen.getByTestId("card-block"));

    expect(screen.getByTestId("modal-overlay")).toBeInTheDocument();
    expect(screen.getByText("Card Detail")).toBeInTheDocument();
    expect(screen.getAllByText("hello").length).toBeGreaterThanOrEqual(2);
  });

  it("opens create card modal when create button is clicked", async () => {
    render(<CardsTab />);
    await waitFor(() => {
      expect(screen.getByText("+")).toBeInTheDocument();
    });
    fireEvent.click(screen.getByText("+"));

    expect(screen.getByText("Create Card")).toBeInTheDocument();
  });

  it("opens edit modal when edit button is clicked", async () => {
    render(<CardsTab />);
    await waitFor(() => {
      expect(screen.getByTestId("btn-edit-card")).toBeInTheDocument();
    });
    fireEvent.click(screen.getByTestId("btn-edit-card"));

    expect(screen.getByText("Edit Card")).toBeInTheDocument();
  });

  it("opens confirm delete modal when delete button is clicked", async () => {
    render(<CardsTab />);
    await waitFor(() => {
      expect(screen.getByTestId("btn-delete-card")).toBeInTheDocument();
    });
    fireEvent.click(screen.getByTestId("btn-delete-card"));

    expect(screen.getByTestId("modal-save")).toHaveTextContent("Delete");
  });

  it("sends DELETE request when delete is confirmed", async () => {
    render(<CardsTab />);
    await waitFor(() => {
      expect(screen.getByTestId("btn-delete-card")).toBeInTheDocument();
    });
    fireEvent.click(screen.getByTestId("btn-delete-card"));
    fireEvent.click(screen.getByTestId("modal-save"));

    await waitFor(() => {
      expect(global.fetch).toHaveBeenCalledWith(
        expect.stringContaining("/api/cards/c1"),
        expect.objectContaining({ method: "DELETE" })
      );
    });
  });

  it("closes modal when Cancel is clicked", async () => {
    render(<CardsTab />);
    await waitFor(() => {
      expect(screen.getByTestId("btn-delete-card")).toBeInTheDocument();
    });
    fireEvent.click(screen.getByTestId("btn-delete-card"));
    expect(screen.getByTestId("modal-overlay")).toBeInTheDocument();

    fireEvent.click(screen.getByTestId("modal-cancel"));
    await waitFor(() => {
      expect(screen.queryByTestId("modal-overlay")).toBeNull();
    });
  });

  it("opens forget modal when forget button is clicked", async () => {
    render(<CardsTab />);
    await waitFor(() => {
      expect(screen.getByTestId("btn-forget-card")).toBeInTheDocument();
    });
    fireEvent.click(screen.getByTestId("btn-forget-card"));

    expect(screen.getByTestId("modal-overlay")).toBeInTheDocument();
    expect(screen.getByText("遗忘卡片")).toBeInTheDocument();
    expect(screen.getByTestId("modal-save")).toHaveTextContent("确认遗忘");
  });

  it("sends POST forget request when forget is confirmed", async () => {
    render(<CardsTab />);
    await waitFor(() => {
      expect(screen.getByTestId("btn-forget-card")).toBeInTheDocument();
    });
    fireEvent.click(screen.getByTestId("btn-forget-card"));
    fireEvent.click(screen.getByTestId("modal-save"));

    await waitFor(() => {
      expect(global.fetch).toHaveBeenCalledWith(
        expect.stringContaining("/api/cards/c1/forget"),
        expect.objectContaining({ method: "POST" })
      );
    });
  });
});
