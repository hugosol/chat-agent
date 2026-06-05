import type { Card } from "../../shared/types";
import { speakText } from "../../shared/tts";
import { truncate } from "../../shared/utils";
import styles from "./CardBlock.module.css";

interface CardBlockProps {
  card: Card;
  onClick: (card: Card) => void;
  onEdit: (card: Card) => void;
  onDelete: (card: Card) => void;
  onForget: (card: Card) => void;
}

function CardBlock({ card, onClick, onEdit, onDelete, onForget }: CardBlockProps): JSX.Element {
  const truncatedBack = truncate(card.back, 100);
  const backLines = truncatedBack.split("\n");

  const handleSpeak = (e: React.MouseEvent) => {
    e.stopPropagation();
    const eng = card.front.replace(/[^A-Za-z\s]/g, "").replace(/\s+/g, " ").trim();
    if (eng) {
      speakText(eng);
    }
  };

  return (
    <div className={styles.block} data-testid="card-block" onClick={() => onClick(card)}>
      <div className={styles.front} data-testid="card-front">
        {card.front}
        <span className={styles.ttsBtn} data-testid="card-tts-btn" onClick={handleSpeak}>
          🔊
        </span>
      </div>
      <div className={styles.back} data-testid="card-back">
        {backLines.map((line, i) => (
          <span key={i}>
            {line}
            {i < backLines.length - 1 && <br />}
          </span>
        ))}
      </div>
      {card.tags.length > 0 && (
        <div className={styles.tags}>
          {card.tags.map((tag) => (
            <span key={tag.id} className="chip" data-testid="card-tag-chip">
              {tag.name}
              {tag.type === "deck" ? " [D]" : ""}
            </span>
          ))}
        </div>
      )}
      <div className={styles.actions}>
        <button data-testid="btn-edit-card" onClick={(e) => { e.stopPropagation(); onEdit(card); }}>
          Edit
        </button>
        <button data-testid="btn-forget-card" onClick={(e) => { e.stopPropagation(); onForget(card); }}>
          Forget
        </button>
        <button data-testid="btn-delete-card" onClick={(e) => { e.stopPropagation(); onDelete(card); }}>
          Delete
        </button>
      </div>
    </div>
  );
}

export { CardBlock };
export type { CardBlockProps };
