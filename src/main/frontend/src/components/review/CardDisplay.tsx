import React, { useState } from "react";
import { speakText } from "../../shared/tts";
import { englishOnly } from "../../shared/utils";
import type { EnhancementData, ReviewCard } from "./reviewTypes";
import styles from "./CardDisplay.module.css";

interface Props {
  front: string;
  back: string;
  cardId?: string;
  flipped: boolean;
  enhancement?: EnhancementData | null;
  onFlip?: () => void;
  onCardUpdated?: (card: ReviewCard) => void;
  onEditingChange?: (editing: boolean) => void;
}
export function CardDisplay({ front, back, cardId, flipped, enhancement, onFlip, onCardUpdated, onEditingChange }: Props): React.ReactElement {
  const [editing, setEditing] = useState(false);
  const [editText, setEditText] = useState("");
  const [saving, setSaving] = useState(false);
  const [enhancing, setEnhancing] = useState(false);
  const [enhanceError, setEnhanceError] = useState("");
  const [localEnhancement, setLocalEnhancement] = useState<EnhancementData | null>(null);

  const activeEnhancement = enhancement || localEnhancement;
  const hasEnhancement = !!(activeEnhancement?.movieQuote || activeEnhancement?.etymology);

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
      const resp = await fetch(`/api/cards/${cardId}/back`, {
        method: "PATCH",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ back: editText.trim() }),
      });
      if (resp.ok) {
        const updated = await resp.json() as ReviewCard;
        onCardUpdated?.(updated);
        setEditing(false);
        onEditingChange?.(false);
      }
    } finally {
      setSaving(false);
    }
  };

  const handleEnhance = async (e: React.MouseEvent) => {
    e.stopPropagation();
    if (!cardId) return;
    setEnhancing(true);
    setEnhanceError("");
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 15000);
    try {
      const resp = await fetch(`/api/cards/${cardId}/enhance`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        credentials: "include",
        signal: controller.signal,
      });
      if (resp.ok) {
        const data = await resp.json() as EnhancementData;
        setLocalEnhancement(data);
      } else {
        setEnhanceError("Enhancement failed");
      }
    } catch (err: unknown) {
      if (err instanceof DOMException && err.name === "AbortError") {
        setEnhanceError("Request timed out");
      } else {
        setEnhanceError("Network error");
      }
    } finally {
      clearTimeout(timeout);
      setEnhancing(false);
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
            <div className={`${styles.cardBack} ${hasEnhancement || enhancing ? styles.scrollableBack : ""}`} data-testid="card-back">
              <button
                data-testid="edit-btn"
                className={styles.ttsBtn}
                onClick={handleEdit}
              >
                {"\u270E"}
              </button>
              <div className={styles.enhanceSection} data-testid="definition-zone">
                <span className={styles.cardText}>{renderText(back)}</span>
              </div>
              {showTtsBack && (
                <button
                  data-testid="tts-btn-back"
                  className={styles.ttsBtn}
                  onClick={(e) => { e.stopPropagation(); speakText(englishOnly(back)); }}
                >
                  {"\uD83D\uDD0A"}
                </button>
              )}

              {activeEnhancement?.movieQuote && (
                <>
                  <hr className={styles.divider} />
                  <div className={styles.enhanceSection} data-testid="movie-quote-zone">
                    <div className={styles.movieQuoteTitle}>
                      {activeEnhancement.movieQuote.movieTitle} [{activeEnhancement.movieQuote.timestamp}]
                    </div>
                    <div className={styles.movieQuoteText}>
                      "{activeEnhancement.movieQuote.quote}"
                    </div>
                    {activeEnhancement.sceneSummary && (
                      <div className={styles.sceneSummary} data-testid="scene-summary">
                        {activeEnhancement.sceneSummary}
                      </div>
                    )}
                  </div>
                </>
              )}

              {activeEnhancement?.etymology && (
                <>
                  <hr className={styles.divider} />
                  <div className={styles.enhanceSection} data-testid="etymology-zone">
                    {activeEnhancement.etymology}
                  </div>
                </>
              )}

              {!hasEnhancement && !enhancing && (
                <button
                  data-testid="card-enhance-btn"
                  className={styles.enhanceBtn}
                  onClick={handleEnhance}
                >
                  Card Enhance
                </button>
              )}

              {enhancing && (
                <div className={styles.loadingOverlay} data-testid="enhance-loading">
                  <div className={styles.spinner} />
                  {enhanceError && (
                    <div className={styles.enhanceError} data-testid="enhance-error">
                      {enhanceError}
                      <button className={styles.enhanceBtn} onClick={handleEnhance}>Retry</button>
                    </div>
                  )}
                </div>
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
