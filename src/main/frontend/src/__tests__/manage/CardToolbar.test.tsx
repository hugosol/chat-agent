import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { render, fireEvent, screen, act } from "@testing-library/react";
import { CardToolbar } from "../../components/manage/CardToolbar";
import type { Tag } from "../../shared/types";

const sampleDecks: Tag[] = [
  { id: "1", name: "Daily English", type: "deck" },
  { id: "2", name: "Work", type: "deck" },
];

const defaultProps = {
  search: "",
  sort: "front,asc",
  deckId: null as string | null,
  decks: sampleDecks,
  onSearchChange: vi.fn(),
  onSortChange: vi.fn(),
  onDeckChange: vi.fn(),
  onCreate: vi.fn(),
  onBatchOpen: vi.fn(),
};

describe("CardToolbar", () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("renders search input with current value", () => {
    render(<CardToolbar {...defaultProps} search="hello" />);
    const input = screen.getByTestId("card-search") as HTMLInputElement;
    expect(input.value).toBe("hello");
  });

  it("renders sort dropdown button with correct label", () => {
    render(<CardToolbar {...defaultProps} sort="front,asc" />);
    const btn = screen.getByTestId("sort-dropdown-btn");
    expect(btn).toHaveTextContent("Aa ↑");
  });

  it("renders sort dropdown button with desc label when active", () => {
    render(<CardToolbar {...defaultProps} sort="front,desc" />);
    const btn = screen.getByTestId("sort-dropdown-btn");
    expect(btn).toHaveTextContent("Aa ↓");
  });

  it("renders deck tabs including '全部'", () => {
    render(<CardToolbar {...defaultProps} />);
    const tabs = screen.getAllByTestId("deck-tab");
    expect(tabs).toHaveLength(3);
    expect(tabs[0]).toHaveTextContent("全部");
    expect(tabs[1]).toHaveTextContent("Daily English");
  });

  it("highlights active deck tab", () => {
    render(<CardToolbar {...defaultProps} deckId={"1"} />);
    const tabs = screen.getAllByTestId("deck-tab");
    expect(tabs[0].getAttribute("data-active")).toBe("false");
    expect(tabs[1].getAttribute("data-active")).toBe("true");
    expect(tabs[2].getAttribute("data-active")).toBe("false");
  });

  it("hides deck tabs row when decks is empty", () => {
    render(<CardToolbar {...defaultProps} decks={[]} />);
    expect(screen.queryByTestId("deck-tab")).not.toBeInTheDocument();
  });

  it("renders create button with + text", () => {
    render(<CardToolbar {...defaultProps} decks={[]} />);
    expect(screen.getByText("+")).toBeInTheDocument();
  });

  it("renders batch dropdown button", () => {
    render(<CardToolbar {...defaultProps} decks={[]} />);
    expect(screen.getByTestId("batch-dropdown-btn")).toBeInTheDocument();
  });

  it("calls onSearchChange after 1000ms debounce on input change", () => {
    const onSearchChange = vi.fn();
    render(<CardToolbar {...defaultProps} decks={[]} onSearchChange={onSearchChange} />);
    const input = screen.getByTestId("card-search");
    fireEvent.change(input, { target: { value: "test" } });

    act(() => {
      vi.advanceTimersByTime(500);
    });
    expect(onSearchChange).not.toHaveBeenCalled();

    act(() => {
      vi.advanceTimersByTime(500);
    });
    expect(onSearchChange).toHaveBeenCalledWith("test");
  });

  it("opens sort dropdown and calls onSortChange when option is clicked", () => {
    const onSortChange = vi.fn();
    render(<CardToolbar {...defaultProps} decks={[]} onSortChange={onSortChange} />);

    fireEvent.click(screen.getByTestId("sort-dropdown-btn"));
    const options = screen.getAllByTestId("sort-option");
    expect(options).toHaveLength(4);

    fireEvent.click(options[1]);
    expect(onSortChange).toHaveBeenCalledWith("front,desc");
  });

  it("calls onBatchOpen when batch option is clicked", () => {
    const onBatchOpen = vi.fn();
    render(<CardToolbar {...defaultProps} decks={[]} onBatchOpen={onBatchOpen} />);

    fireEvent.click(screen.getByTestId("batch-dropdown-btn"));
    const options = screen.getAllByTestId("batch-option");
    expect(options).toHaveLength(2);

    fireEvent.click(options[0]);
    expect(onBatchOpen).toHaveBeenCalledWith("export");
  });

  it("calls onDeckChange when deck tab is clicked", () => {
    const onDeckChange = vi.fn();
    render(<CardToolbar {...defaultProps} onDeckChange={onDeckChange} />);
    fireEvent.click(screen.getAllByTestId("deck-tab")[1]);
    expect(onDeckChange).toHaveBeenCalledWith("1");
  });

  it("calls onDeckChange with null when '全部' tab is clicked", () => {
    const onDeckChange = vi.fn();
    render(<CardToolbar {...defaultProps} onDeckChange={onDeckChange} deckId={"1"} />);
    fireEvent.click(screen.getAllByTestId("deck-tab")[0]);
    expect(onDeckChange).toHaveBeenCalledWith(null);
  });

  it("calls onCreate when create button is clicked", () => {
    const onCreate = vi.fn();
    render(<CardToolbar {...defaultProps} decks={[]} onCreate={onCreate} />);
    fireEvent.click(screen.getByText("+"));
    expect(onCreate).toHaveBeenCalledOnce();
  });
});
