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
  const [showConfirm, setShowConfirm] = useState(false);

  const activeEnhancement = enhancement || localEnhancement;

  const handleMagnifierClick = (e: React.MouseEvent) => {
    e.stopPropagation();
    setShowConfirm(true);
  };

  const handleConfirmEnhance = (e: React.MouseEvent) => {
    e.stopPropagation();
    setShowConfirm(false);
    handleEnhance(e);
  };

  const handleCancelEnhance = (e: React.MouseEvent) => {
    e.stopPropagation();
    setShowConfirm(false);
  };

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
        // Only treat as enhancement if any data field is actually non-null
        if (data.movieQuote != null || data.sceneSummary != null || data.etymology != null) {
          setLocalEnhancement(data);
        } else {
          setEnhanceError("No enhancement data available");
        }
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
            <div className={`${styles.cardBack} ${activeEnhancement != null || enhancing ? styles.scrollableBack : ""}`} data-testid="card-back">
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

              {activeEnhancement != null && (
                <>
                  <hr className={styles.divider} />
                  <div className={styles.enhanceSection} data-testid="movie-quote-zone">
                    {activeEnhancement?.movieQuote ? (
                      <>
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
                      </>
                    ) : (
                      <span data-testid="movie-quote-placeholder">【暂无电影台词数据】</span>
                    )}
                  </div>
                  <hr className={styles.divider} />
                  <div className={styles.enhanceSection} data-testid="etymology-zone">
                    {activeEnhancement?.etymology ? (
                      activeEnhancement.etymology
                    ) : (
                      <span data-testid="etymology-placeholder">【暂无词源数据】</span>
                    )}
                  </div>
                </>
              )}

              {activeEnhancement == null && !enhancing && (
                <button
                  data-testid="card-enhance-btn"
                  className={styles.enhanceBtn}
                  onClick={handleMagnifierClick}
                >
                  {"\uD83D\uDD0D"}
                </button>
              )}

              {showConfirm && (
                <div className={styles.loadingOverlay} data-testid="enhance-confirm-dialog">
                  <div className={styles.enhanceError}>
                    <p>是否获取更多信息？</p>
                    <button
                      data-testid="enhance-confirm-ok"
                      className={styles.enhanceBtn}
                      onClick={handleConfirmEnhance}
                    >
                      确认
                    </button>
                    <button
                      data-testid="enhance-confirm-cancel"
                      className={styles.enhanceBtn}
                      onClick={handleCancelEnhance}
                    >
                      取消
                    </button>
                  </div>
                </div>
              )}

              {enhancing && (
                <div className={styles.loadingOverlay} data-testid="enhance-loading">
                  <div className={styles.spinner} />
                </div>
              )}
              {!enhancing && enhanceError && (
                <div className={styles.enhanceError} data-testid="enhance-error">
                  {enhanceError}
                  <button className={styles.enhanceBtn} onClick={handleEnhance}>Retry</button>
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
