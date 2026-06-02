import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, fireEvent, screen, waitFor } from "@testing-library/react";
import { CardsTab } from "../../components/manage/CardsTab";
import type { Card, Tag, PageResponse } from "../../shared/types";

const mockCards: Card[] = [
  {
    id: "c1", front: "hello", back: "你好\nexplanation", tags: [],
    due: "2025-06-15T00:00:00", cardState: 0, createTime: "2025-01-01T00:00:00",
  },
];

const mockDecks: Tag[] = [
  { id: 1, name: "Daily", type: "deck" },
];

const mockAllTags: Tag[] = [
  { id: 1, name: "Daily", type: "deck" },
  { id: 2, name: "verb", type: null },
];

function mockCardResponse(cards: Card[], totalPages = 1): PageResponse<Card> {
  return { content: cards, totalPages, totalElements: cards.length, number: 0, size: 10 };
}

function setupFetchMocks() {
  let callCount = 0;
  (global as any).fetch = vi.fn((url: string) => {
    const urlStr = String(url);
    if (urlStr.includes("/api/cards")) {
      return Promise.resolve({
        ok: true,
        json: () => Promise.resolve(mockCardResponse(mockCards)),
      });
    }
    if (urlStr.includes("/api/tags?type=deck")) {
      return Promise.resolve({
        ok: true,
        json: () => Promise.resolve(mockDecks),
      });
    }
    if (urlStr === "/api/tags") {
      callCount++;
      return Promise.resolve({
        ok: true,
        json: () => Promise.resolve(callCount === 1 ? mockAllTags : mockDecks),
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

  it("loads and displays deck chips", async () => {
    render(<CardsTab />);
    await waitFor(() => {
      expect(screen.getByTestId("deck-chip")).toBeInTheDocument();
    });
    expect(screen.getByTestId("deck-chip")).toHaveTextContent("Daily");
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
      expect(screen.getByText("+ 创建卡片")).toBeInTheDocument();
    });
    fireEvent.click(screen.getByText("+ 创建卡片"));

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
});
