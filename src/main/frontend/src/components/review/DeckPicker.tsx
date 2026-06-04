import React, { useState, useEffect } from "react";
import type { DeckInfo, ReviewMode } from "./reviewTypes";
import styles from "./DeckPicker.module.css";

interface Props {
  onStart: (deck: DeckInfo, mode: ReviewMode, limit: number) => void;
}

const MODES: { value: ReviewMode; label: string; desc: string; showLimit: boolean }[] = [
  { value: "STANDARD", label: "标准复习", desc: "新卡 + 到期卡，按到期时间排序", showLimit: true },
  { value: "REVIEW_ONLY", label: "仅复习", desc: "仅复习已学到期卡片", showLimit: false },
  { value: "NEW_ONLY", label: "仅新卡", desc: "仅学习新卡片，按创建时间排序", showLimit: true },
  { value: "CRAM", label: "速通", desc: "全卡组随机顺序，不限到期时间", showLimit: false },
];

export function DeckPicker({ onStart }: Props): React.ReactElement {
  const [decks, setDecks] = useState<DeckInfo[]>([]);
  const [selectedDeckId, setSelectedDeckId] = useState<string>("");
  const [selectedMode, setSelectedMode] = useState<ReviewMode>("STANDARD");
  const [limit, setLimit] = useState(20);
  const [loading, setLoading] = useState(true);
  const [learnedToday, setLearnedToday] = useState(0);

  useEffect(() => {
    loadDecks();
    loadPreferences();
  }, []);

  const loadDecks = async () => {
    try {
      const res = await fetch("/api/review/decks", { credentials: "same-origin" });
      if (res.ok) {
        const data: DeckInfo[] = await res.json();
        setDecks(data);
      }
    } finally {
      setLoading(false);
    }
  };

  const loadPreferences = async () => {
    try {
      const res = await fetch("/api/user/preferences", { credentials: "same-origin" });
      if (res.ok) {
        const prefs = await res.json();
        if (prefs.lastDeckId) setSelectedDeckId(prefs.lastDeckId);
        if (prefs.lastMode) setSelectedMode(prefs.lastMode as ReviewMode);
        if (prefs.newCardDailyLimit) setLimit(prefs.newCardDailyLimit);
      }
    } catch {
      // ignore
    }
  };

  const selectedDeck = decks.find((d) => d.id === selectedDeckId);
  const showLimit = MODES.find((m) => m.value === selectedMode)?.showLimit ?? false;

  const handleStart = async () => {
    if (!selectedDeck) return;

    const effectiveLimit = showLimit ? limit : 0;
    if (showLimit && learnedToday >= effectiveLimit) {
      const confirmed = window.confirm(
        `今日新卡已达上限（已学 ${learnedToday} / 上限 ${effectiveLimit}），今日不再引入新卡。仍然继续复习吗？`
      );
      if (!confirmed) return;
    }

    try {
      await fetch("/api/user/preferences", {
        method: "PUT",
        credentials: "same-origin",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ lastDeckId: selectedDeck.id, lastMode: selectedMode }),
      });
    } catch {
      // ignore
    }

    onStart(selectedDeck, selectedMode, effectiveLimit);
  };

  const fetchStatsForDeck = async (deckId: string) => {
    if (!deckId) return;
    try {
      const res = await fetch(
        `/api/review/stats?deckId=${deckId}&mode=${selectedMode}&limit=${limit}`,
        { credentials: "same-origin" }
      );
      if (res.ok) {
        const data = await res.json();
        setLearnedToday(data.learnedToday ?? 0);
      }
    } catch {
      // ignore
    }
  };

  useEffect(() => {
    fetchStatsForDeck(selectedDeckId);
  }, [selectedDeckId, selectedMode, limit]);

  if (loading) {
    return <div className={styles.container}>Loading...</div>;
  }

  return (
    <div className={styles.container}>
      <h1 className={styles.title}>复习</h1>

      <div className={styles.section}>
        <h2 className={styles.sectionTitle}>选择牌组</h2>
        {decks.length === 0 ? (
          <p className={styles.empty}>暂无牌组，请先在管理页面创建 Deck 标签并添加卡片</p>
        ) : (
          <div className={styles.deckList}>
            {decks.map((deck) => (
              <label
                key={deck.id}
                data-testid="deck-item"
                className={`${styles.deckItem} ${selectedDeckId === deck.id ? styles.deckItemActive : ""}`}
              >
                <input
                  type="radio"
                  name="deck"
                  value={deck.id}
                  checked={selectedDeckId === deck.id}
                  onChange={() => setSelectedDeckId(deck.id)}
                  className={styles.radio}
                />
                <span className={styles.deckName}>{deck.name}</span>
                <span className={styles.deckCount}>{deck.cardCount} 张</span>
              </label>
            ))}
          </div>
        )}
      </div>

      <div className={styles.section}>
        <h2 className={styles.sectionTitle}>选择模式</h2>
        <div className={styles.modeList}>
          {MODES.map((mode) => (
            <label
              key={mode.value}
              data-testid="mode-item"
              className={`${styles.modeItem} ${selectedMode === mode.value ? styles.modeItemActive : ""}`}
            >
              <input
                type="radio"
                name="mode"
                value={mode.value}
                checked={selectedMode === mode.value}
                onChange={() => setSelectedMode(mode.value)}
                className={styles.radio}
              />
              <div>
                <span className={styles.modeLabel}>{mode.label}</span>
                <span className={styles.modeDesc}>{mode.desc}</span>
              </div>
            </label>
          ))}
        </div>
      </div>

      {showLimit && (
        <div className={styles.section} data-testid="limit-section">
          <h2 className={styles.sectionTitle}>每日新卡上限</h2>
          <input
            type="number"
            data-testid="limit-input"
            className={styles.limitInput}
            min={0}
            value={limit}
            onChange={(e) => setLimit(Number(e.target.value))}
          />
          {learnedToday > 0 && (
            <span className={styles.learnedInfo}>今日已学新卡: {learnedToday}</span>
          )}
        </div>
      )}

      <button
        data-testid="start-btn"
        className={styles.startBtn}
        disabled={!selectedDeckId || !selectedMode}
        onClick={handleStart}
      >
        开始复习
      </button>
    </div>
  );
}
