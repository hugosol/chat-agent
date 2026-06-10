import React, { useState } from "react";
import { speakText } from "../../shared/tts";
import { englishOnly } from "../../shared/utils";
import type { ReviewCard } from "./reviewTypes";
import styles from "./CardDisplay.module.css";

interface Props {
  front: string;
  back: string;
  cardId?: string;
  flipped: boolean;
  onFlip?: () => void;
  onCardUpdated?: (card: ReviewCard) => void;
  onEditingChange?: (editing: boolean) => void;
}
export function CardDisplay({ front, back, cardId, flipped, onFlip, onCardUpdated, onEditingChange }: Props): React.ReactElement {
  const [editing, setEditing] = useState(false);
  const [editText, setEditText] = useState("");
  const [saving, setSaving] = useState(false);

  const handleFlip = () => {
    onFlip?.();
  };

  const handleEdit = (e: React.MouseEvent) => {
    e.stopPropagation();
    setEditText(back);
    setEditing(true);
    onEditingChange?.(true);
  };

  const handleCancel = () => {
    setEditing(false);
    onEditingChange?.(false);
  };

  const handleSave = async () => {
    if (!editText.trim() || !cardId) return;
    setSaving(true);
    try {
      const res = await fetch(`/api/cards/${cardId}/back`, {
        method: "PATCH",
        credentials: "same-origin",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ back: editText.trim() }),
      });
      if (res.ok) {
        const updated: ReviewCard = await res.json();
        onCardUpdated?.(updated);
        setEditing(false);
        onEditingChange?.(false);
      }
    } finally {
      setSaving(false);
    }
  };

  const showTtsFront = englishOnly(front).length > 0;
  const showTtsBack = englishOnly(back).length > 0;

  const renderText = (text: string) => {
    const lines = text.split("\n");
    return lines.map((line, i) => (
      <span key={i}>
        {line}
        {i < lines.length - 1 && <br />}
      </span>
    ));
  };

  return (
    <div className={styles.cardArea}>
      <div
        data-testid="flip-card-btn"
        className={`${styles.card} ${flipped ? styles.cardFlipped : ""}`}
        onClick={!flipped ? handleFlip : undefined}
      >
        <div className={styles.cardInner}>
          <div className={styles.cardFront} data-testid="card-front">
            <span className={styles.cardText}>{renderText(front)}</span>
            {flipped && showTtsFront && (
              <button
                data-testid="tts-btn-front"
                className={styles.ttsBtn}
                onClick={(e) => { e.stopPropagation(); speakText(englishOnly(front)); }}
              >
                {"\uD83D\uDD0A"}
              </button>
            )}
          </div>
          {flipped && !editing && (
            <div className={styles.cardBack} data-testid="card-back">
              <button
                data-testid="edit-btn"
                className={styles.ttsBtn}
                onClick={handleEdit}
              >
                {"\u270E"}
              </button>
              <span className={styles.cardText}>{renderText(back)}</span>
              {showTtsBack && (
                <button
                  data-testid="tts-btn-back"
                  className={styles.ttsBtn}
                  onClick={(e) => { e.stopPropagation(); speakText(englishOnly(back)); }}
                >
                  {"\uD83D\uDD0A"}
                </button>
              )}
            </div>
          )}
          {editing && (
            <div className={styles.editArea} data-testid="edit-area">
              <textarea
                data-testid="edit-textarea"
                className={styles.editTextarea}
                value={editText}
                onChange={(e) => setEditText(e.target.value)}
              />
              <div className={styles.editBtns}>
                <button
                  data-testid="edit-save"
                  className={styles.editSaveBtn}
                  onClick={handleSave}
                  disabled={saving || !editText.trim()}
                >
                  Save
                </button>
                <button
                  data-testid="edit-cancel"
                  className={styles.editCancelBtn}
                  onClick={handleCancel}
                >
                  Cancel
                </button>
              </div>
            </div>
          )}
        </div>
        {!flipped && (
          <p className={styles.tapHint}>Tap to reveal</p>
        )}
      </div>
      {!flipped && showTtsFront && (
        <button
          data-testid="tts-btn-below"
          className={styles.ttsBtnBelow}
          onClick={() => speakText(englishOnly(front))}
        >
          {"\uD83D\uDD0A"}
        </button>
      )}
    </div>
  );
}
