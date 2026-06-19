import React, { useState } from "react";
import { speakText } from "../../shared/tts";
import { englishOnly } from "../../shared/utils";
import { showToast } from "../../shared/Toast";
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
  const [requoting, setRequoting] = useState(false);
  const [showRequoteConfirm, setShowRequoteConfirm] = useState(false);

  const activeEnhancement = localEnhancement || enhancement;

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

  const handleRequoteClick = (e: React.MouseEvent) => {
    e.stopPropagation();
    setShowRequoteConfirm(true);
  };

  const handleRequoteConfirm = (e: React.MouseEvent) => {
    e.stopPropagation();
    setShowRequoteConfirm(false);
    handleRequote(e);
  };

  const handleRequoteCancel = (e: React.MouseEvent) => {
    e.stopPropagation();
    setShowRequoteConfirm(false);
  };

  const handleRequote = async (e: React.MouseEvent) => {
    e.stopPropagation();
    if (!cardId) return;
    setRequoting(true);
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 15000);
    try {
      const currentQuote = activeEnhancement?.movieQuote;
      const body: Record<string, string> = {};
      if (currentQuote) {
        body.imdbId = currentQuote.imdbId;
        body.timestamp = currentQuote.timestamp;
      }
      const resp = await fetch(`/api/cards/${cardId}/enhance/requote`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        credentials: "include",
        signal: controller.signal,
        body: JSON.stringify(body),
      });
      if (resp.ok) {
        const data = await resp.json();
        if (data.found === false) {
          const reason = data.reason;
          if (reason === "no_movies") {
            showToast("未导入任何电影，无法搜索台词");
          } else if (reason === "no_subtitle_match") {
            showToast("你的电影库中没有包含该词的对白");
          } else if (reason === "no_other_candidates") {
            showToast("该词仅有一处匹配，没有其他可替换的台词");
          } else {
            showToast("没有其他可替换的台词");
          }
        } else {
          const movieTitle = data.movieQuote?.movieTitle || "";
          setLocalEnhancement((prev) => ({
            ...prev,
            movieQuote: data.movieQuote ?? null,
            sceneSummary: data.sceneSummary ?? prev?.sceneSummary ?? null,
            etymology: prev?.etymology ?? null,
          }));
          showToast(movieTitle ? `已替换为《${movieTitle}》的台词` : "已替换台词");
        }
      } else {
        showToast("替换失败，请重试");
      }
    } catch (err: unknown) {
      if (err instanceof DOMException && err.name === "AbortError") {
        showToast("请求超时，请重试");
      } else {
        showToast("网络错误");
      }
    } finally {
      clearTimeout(timeout);
      setRequoting(false);
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
                className={styles.cornerTopLeft}
                onClick={handleEdit}
              >
                {"\u270E"}
              </button>
              <div className={styles.definitionZone} data-testid="definition-zone">
                <span className={styles.cardText}>{renderText(back)}</span>
              </div>
              {showTtsBack && (
                <button
                  data-testid="tts-btn-back"
                  className={styles.cornerTopRight}
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
                        <div className={styles.movieQuoteHeader}>
                          <span className={styles.movieQuoteTitle}>
                            {activeEnhancement.movieQuote.movieTitle} [{activeEnhancement.movieQuote.timestamp}]
                          </span>
                          <span className={styles.movieQuoteActions}>
                            <button
                              data-testid="tts-btn-quote"
                              className={styles.ttsBtn}
                              onClick={(e) => { e.stopPropagation(); speakText(activeEnhancement.movieQuote!.quote); }}
                            >
                              {"\uD83D\uDD0A"}
                            </button>
                            <button
                              data-testid="requote-btn"
                              className={styles.ttsBtn}
                              onClick={handleRequoteClick}
                            >
                              {"\uD83D\uDD04"}
                            </button>
                          </span>
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
                      <>
                        <span data-testid="movie-quote-placeholder">【暂无电影台词数据】</span>
                        <button
                          data-testid="requote-btn"
                          className={styles.ttsBtn}
                          onClick={handleRequoteClick}
                          style={{ marginLeft: 8 }}
                        >
                          {"\uD83D\uDD04"}
                        </button>
                      </>
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
                  className={styles.cornerBottomRight}
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

              {showRequoteConfirm && (
                <div className={styles.loadingOverlay} data-testid="requote-confirm-dialog">
                  <div className={styles.enhanceError}>
                    <p>{activeEnhancement?.movieQuote ? "替换为另一句电影台词？" : "搜索电影台词？"}</p>
                    <button
                      data-testid="requote-confirm-ok"
                      className={styles.enhanceBtn}
                      onClick={handleRequoteConfirm}
                    >
                      确认
                    </button>
                    <button
                      data-testid="requote-confirm-cancel"
                      className={styles.enhanceBtn}
                      onClick={handleRequoteCancel}
                    >
                      取消
                    </button>
                  </div>
                </div>
              )}

              {requoting && (
                <div className={styles.loadingOverlay} data-testid="requote-loading">
                  <div className={styles.spinner} />
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
