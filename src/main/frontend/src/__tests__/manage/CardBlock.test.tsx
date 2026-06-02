import { describe, it, expect, vi } from "vitest";
import { render, fireEvent, screen } from "@testing-library/react";
import { CardBlock } from "../../components/manage/CardBlock";
import type { Card } from "../../shared/types";

const baseCard: Card = {
  id: "abc-123",
  front: "hello world",
  back: "你好世界\nThe greeting",
  tags: [
    { id: 1, name: "greeting", type: null },
    { id: 2, name: "basic", type: "deck" },
  ],
  due: "2025-01-15T00:00:00",
  cardState: 0,
  createTime: "2025-01-01T00:00:00",
};

describe("CardBlock", () => {
  it("renders card front and back text", () => {
    render(
      <CardBlock card={baseCard} onClick={vi.fn()} onEdit={vi.fn()} onDelete={vi.fn()} />
    );
    expect(screen.getByTestId("card-front")).toHaveTextContent("hello world");
    expect(screen.getByTestId("card-back")).toHaveTextContent("你好世界");
  });

  it("renders TTS button on front", () => {
    render(
      <CardBlock card={baseCard} onClick={vi.fn()} onEdit={vi.fn()} onDelete={vi.fn()} />
    );
    expect(screen.getByTestId("card-tts-btn")).toBeInTheDocument();
  });

  it("renders tag chips", () => {
    render(
      <CardBlock card={baseCard} onClick={vi.fn()} onEdit={vi.fn()} onDelete={vi.fn()} />
    );
    const chips = screen.getAllByTestId("card-tag-chip");
    expect(chips).toHaveLength(2);
    expect(chips[0]).toHaveTextContent("greeting");
    expect(chips[1]).toHaveTextContent("basic [D]");
  });

  it("calls onClick when card block is clicked", () => {
    const onClick = vi.fn();
    render(
      <CardBlock card={baseCard} onClick={onClick} onEdit={vi.fn()} onDelete={vi.fn()} />
    );
    fireEvent.click(screen.getByTestId("card-block"));
    expect(onClick).toHaveBeenCalledWith(baseCard);
  });

  it("calls onEdit when Edit button is clicked", () => {
    const onEdit = vi.fn();
    render(
      <CardBlock card={baseCard} onClick={vi.fn()} onEdit={onEdit} onDelete={vi.fn()} />
    );
    fireEvent.click(screen.getByTestId("btn-edit-card"));
    expect(onEdit).toHaveBeenCalledWith(baseCard);
  });

  it("calls onDelete when Delete button is clicked", () => {
    const onDelete = vi.fn();
    render(
      <CardBlock card={baseCard} onClick={vi.fn()} onEdit={vi.fn()} onDelete={onDelete} />
    );
    fireEvent.click(screen.getByTestId("btn-delete-card"));
    expect(onDelete).toHaveBeenCalledWith(baseCard);
  });

  it("does not trigger onClick when Edit or Delete button is clicked", () => {
    const onClick = vi.fn();
    render(
      <CardBlock card={baseCard} onClick={onClick} onEdit={vi.fn()} onDelete={vi.fn()} />
    );
    fireEvent.click(screen.getByTestId("btn-edit-card"));
    fireEvent.click(screen.getByTestId("btn-delete-card"));
    expect(onClick).not.toHaveBeenCalled();
  });

  it("truncates back text at 100 characters", () => {
    const longCard = {
      ...baseCard,
      back: "a".repeat(120),
    };
    render(
      <CardBlock card={longCard} onClick={vi.fn()} onEdit={vi.fn()} onDelete={vi.fn()} />
    );
    const backEl = screen.getByTestId("card-back");
    expect(backEl.textContent!.length).toBeLessThanOrEqual(103); // 100 + "..." = 103
  });

  it("renders empty state for card with no tags", () => {
    const noTagCard = { ...baseCard, tags: [] };
    render(
      <CardBlock card={noTagCard} onClick={vi.fn()} onEdit={vi.fn()} onDelete={vi.fn()} />
    );
    expect(screen.queryByTestId("card-tag-chip")).toBeNull();
  });
});
