import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { render, fireEvent, screen, act } from "@testing-library/react";
import { CardToolbar } from "../../components/manage/CardToolbar";
import type { Tag } from "../../shared/types";

const sampleDecks: Tag[] = [
  { id: "1", name: "Daily English", type: "deck" },
  { id: "2", name: "Work", type: "deck" },
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

  it("renders deck tabs including '全部'", () => {
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
    const tabs = screen.getAllByTestId("deck-tab");
    // '全部' + 2 decks = 3 tabs
    expect(tabs).toHaveLength(3);
    expect(tabs[0]).toHaveTextContent("全部");
    expect(tabs[1]).toHaveTextContent("Daily English");
  });

  it("highlights active deck tab", () => {
    render(
      <CardToolbar
        search=""
        sort="front,asc"
        deckId={"1"}
        decks={sampleDecks}
        onSearchChange={vi.fn()}
        onSortChange={vi.fn()}
        onDeckChange={vi.fn()}
        onCreate={vi.fn()}
      />
    );
    const tabs = screen.getAllByTestId("deck-tab");
    expect(tabs[0].getAttribute("data-active")).toBe("false");
    expect(tabs[1].getAttribute("data-active")).toBe("true");
    expect(tabs[2].getAttribute("data-active")).toBe("false");
  });

  it("hides deck tabs row when decks is empty", () => {
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
    expect(screen.queryByTestId("deck-tab")).not.toBeInTheDocument();
  });

  it("renders create button with + text", () => {
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
    expect(screen.getByText("+")).toBeInTheDocument();
  });

  it("calls onSearchChange after 1000ms debounce on input change", () => {
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

    // should not fire before debounce
    act(() => {
      vi.advanceTimersByTime(500);
    });
    expect(onSearchChange).not.toHaveBeenCalled();

    // should fire after debounce
    act(() => {
      vi.advanceTimersByTime(500);
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

  it("calls onDeckChange when deck tab is clicked", () => {
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
    fireEvent.click(screen.getAllByTestId("deck-tab")[1]);
    expect(onDeckChange).toHaveBeenCalledWith("1");
  });

  it("calls onDeckChange with null when '全部' tab is clicked", () => {
    const onDeckChange = vi.fn();
    render(
      <CardToolbar
        search=""
        sort="front,asc"
        deckId={"1"}
        decks={sampleDecks}
        onSearchChange={vi.fn()}
        onSortChange={vi.fn()}
        onDeckChange={onDeckChange}
        onCreate={vi.fn()}
      />
    );
    fireEvent.click(screen.getAllByTestId("deck-tab")[0]);
    expect(onDeckChange).toHaveBeenCalledWith(null);
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
    fireEvent.click(screen.getByText("+"));
    expect(onCreate).toHaveBeenCalledOnce();
  });
});
