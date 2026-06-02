import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { render, fireEvent, screen, act } from "@testing-library/react";
import { CardToolbar } from "../../components/manage/CardToolbar";
import type { Tag } from "../../shared/types";

const sampleDecks: Tag[] = [
  { id: 1, name: "Daily English", type: "deck" },
  { id: 2, name: "Work", type: "deck" },
];

describe("CardToolbar", () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("renders search input with current value", () => {
    render(
      <CardToolbar
        search="hello"
        sort="front,asc"
        deckId={null}
        decks={sampleDecks}
        onSearchChange={vi.fn()}
        onSortChange={vi.fn()}
        onDeckChange={vi.fn()}
        onCreate={vi.fn()}
      />
    );
    const input = screen.getByTestId("card-search") as HTMLInputElement;
    expect(input.value).toBe("hello");
  });

  it("renders sort buttons with active state", () => {
    render(
      <CardToolbar
        search=""
        sort="front,asc"
        deckId={null}
        decks={sampleDecks}
        onSearchChange={vi.fn()}
        onSortChange={vi.fn()}
        onDeckChange={vi.fn()}
        onCreate={vi.fn()}
      />
    );
    const nameBtn = screen.getByTestId("sort-btn-name");
    const timeBtn = screen.getByTestId("sort-btn-time");
    expect(nameBtn.getAttribute("data-active")).toBe("true");
    expect(timeBtn.getAttribute("data-active")).toBe("false");
  });

  it("renders deck chips", () => {
    render(
      <CardToolbar
        search=""
        sort="front,asc"
        deckId={null}
        decks={sampleDecks}
        onSearchChange={vi.fn()}
        onSortChange={vi.fn()}
        onDeckChange={vi.fn()}
        onCreate={vi.fn()}
      />
    );
    const chips = screen.getAllByTestId("deck-chip");
    expect(chips).toHaveLength(2);
    expect(chips[0]).toHaveTextContent("Daily English");
  });

  it("highlights active deck chip", () => {
    render(
      <CardToolbar
        search=""
        sort="front,asc"
        deckId={1}
        decks={sampleDecks}
        onSearchChange={vi.fn()}
        onSortChange={vi.fn()}
        onDeckChange={vi.fn()}
        onCreate={vi.fn()}
      />
    );
    const chips = screen.getAllByTestId("deck-chip");
    expect(chips[0].getAttribute("data-active")).toBe("true");
    expect(chips[1].getAttribute("data-active")).toBe("false");
  });

  it("renders create card button", () => {
    render(
      <CardToolbar
        search=""
        sort="front,asc"
        deckId={null}
        decks={[]}
        onSearchChange={vi.fn()}
        onSortChange={vi.fn()}
        onDeckChange={vi.fn()}
        onCreate={vi.fn()}
      />
    );
    expect(screen.getByText("+ 创建卡片")).toBeInTheDocument();
  });

  it("calls onSearchChange after debounce on input change", () => {
    const onSearchChange = vi.fn();
    render(
      <CardToolbar
        search=""
        sort="front,asc"
        deckId={null}
        decks={[]}
        onSearchChange={onSearchChange}
        onSortChange={vi.fn()}
        onDeckChange={vi.fn()}
        onCreate={vi.fn()}
      />
    );
    const input = screen.getByTestId("card-search");
    fireEvent.change(input, { target: { value: "test" } });

    act(() => {
      vi.advanceTimersByTime(300);
    });

    expect(onSearchChange).toHaveBeenCalledWith("test");
  });

  it("calls onSortChange when sort button is clicked", () => {
    const onSortChange = vi.fn();
    render(
      <CardToolbar
        search=""
        sort="front,asc"
        deckId={null}
        decks={[]}
        onSearchChange={vi.fn()}
        onSortChange={onSortChange}
        onDeckChange={vi.fn()}
        onCreate={vi.fn()}
      />
    );
    fireEvent.click(screen.getByTestId("sort-btn-time"));
    expect(onSortChange).toHaveBeenCalledWith("createTime,desc");
  });

  it("calls onDeckChange when deck chip is clicked", () => {
    const onDeckChange = vi.fn();
    render(
      <CardToolbar
        search=""
        sort="front,asc"
        deckId={null}
        decks={sampleDecks}
        onSearchChange={vi.fn()}
        onSortChange={vi.fn()}
        onDeckChange={onDeckChange}
        onCreate={vi.fn()}
      />
    );
    fireEvent.click(screen.getAllByTestId("deck-chip")[0]);
    expect(onDeckChange).toHaveBeenCalledWith("1");
  });

  it("calls onCreate when create button is clicked", () => {
    const onCreate = vi.fn();
    render(
      <CardToolbar
        search=""
        sort="front,asc"
        deckId={null}
        decks={[]}
        onSearchChange={vi.fn()}
        onSortChange={vi.fn()}
        onDeckChange={vi.fn()}
        onCreate={onCreate}
      />
    );
    fireEvent.click(screen.getByText("+ 创建卡片"));
    expect(onCreate).toHaveBeenCalledOnce();
  });
});
