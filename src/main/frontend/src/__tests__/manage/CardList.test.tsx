import { describe, it, expect, vi } from "vitest";
import { render, fireEvent, screen } from "@testing-library/react";
import { CardList } from "../../components/manage/CardList";
import type { Card } from "../../shared/types";

const sampleCards: Card[] = [
  {
    id: "a1", front: "hello", back: "你好", tags: [],
    due: null, cardState: 0, createTime: null,
  },
  {
    id: "b2", front: "world", back: "世界", tags: [],
    due: null, cardState: 0, createTime: null,
  },
];

describe("CardList", () => {
  it("renders empty state when no cards", () => {
    render(
      <CardList
        cards={[]}
        page={0}
        totalPages={0}
        onPageChange={vi.fn()}
        onCardClick={vi.fn()}
        onCardEdit={vi.fn()}
        onCardDelete={vi.fn()}
      />
    );
    expect(screen.getByTestId("empty-state")).toHaveTextContent("暂无卡片");
  });

  it("renders all cards and pagination", () => {
    render(
      <CardList
        cards={sampleCards}
        page={0}
        totalPages={1}
        onPageChange={vi.fn()}
        onCardClick={vi.fn()}
        onCardEdit={vi.fn()}
        onCardDelete={vi.fn()}
      />
    );
    expect(screen.getAllByTestId("card-block")).toHaveLength(2);
  });

  it("forwards click, edit, delete handlers to CardBlock", () => {
    const onCardClick = vi.fn();
    const onCardEdit = vi.fn();
    const onCardDelete = vi.fn();
    render(
      <CardList
        cards={sampleCards}
        page={0}
        totalPages={1}
        onPageChange={vi.fn()}
        onCardClick={onCardClick}
        onCardEdit={onCardEdit}
        onCardDelete={onCardDelete}
      />
    );
    fireEvent.click(screen.getAllByTestId("card-block")[0]);
    expect(onCardClick).toHaveBeenCalledWith(sampleCards[0]);

    fireEvent.click(screen.getAllByTestId("btn-edit-card")[1]);
    expect(onCardEdit).toHaveBeenCalledWith(sampleCards[1]);

    fireEvent.click(screen.getAllByTestId("btn-delete-card")[0]);
    expect(onCardDelete).toHaveBeenCalledWith(sampleCards[0]);
  });

  it("renders pagination when totalPages > 1", () => {
    render(
      <CardList
        cards={sampleCards}
        page={0}
        totalPages={3}
        onPageChange={vi.fn()}
        onCardClick={vi.fn()}
        onCardEdit={vi.fn()}
        onCardDelete={vi.fn()}
      />
    );
    expect(screen.getByTestId("pagination")).toBeInTheDocument();
  });

  it("calls onPageChange when pagination changes", () => {
    const onPageChange = vi.fn();
    render(
      <CardList
        cards={sampleCards}
        page={0}
        totalPages={3}
        onPageChange={onPageChange}
        onCardClick={vi.fn()}
        onCardEdit={vi.fn()}
        onCardDelete={vi.fn()}
      />
    );
    fireEvent.click(screen.getByTestId("page-next"));
    expect(onPageChange).toHaveBeenCalledWith(1);
  });
});
