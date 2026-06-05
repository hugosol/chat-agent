import type { Card } from "../../shared/types";
import { CardBlock } from "./CardBlock";
import { Pagination } from "../../shared/Pagination";

interface CardListProps {
  cards: Card[];
  page: number;
  totalPages: number;
  onPageChange: (page: number) => void;
  onCardClick: (card: Card) => void;
  onCardEdit: (card: Card) => void;
  onCardForget: (card: Card) => void;
  onCardDelete: (card: Card) => void;
}

function CardList({
  cards,
  page,
  totalPages,
  onPageChange,
  onCardClick,
  onCardEdit,
  onCardForget,
  onCardDelete,
}: CardListProps): JSX.Element {
  if (cards.length === 0) {
    return (
      <div className="empty-state" data-testid="empty-state">
        暂无卡片
      </div>
    );
  }

  return (
    <div>
      {cards.map((card) => (
        <CardBlock
          key={card.id}
          card={card}
          onClick={onCardClick}
          onEdit={onCardEdit}
          onForget={onCardForget}
          onDelete={onCardDelete}
        />
      ))}
      <Pagination page={page} totalPages={totalPages} onPageChange={onPageChange} />
    </div>
  );
}

export { CardList };
export type { CardListProps };
