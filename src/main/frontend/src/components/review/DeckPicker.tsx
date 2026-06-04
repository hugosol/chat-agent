import React, { useState, useEffect } from "react";
import type { DeckInfo, ReviewMode } from "./reviewTypes";
import styles from "./DeckPicker.module.css";

interface Props {
  onStart: (deck: DeckInfo, mode: ReviewMode, limit: number) => void;
}

const MODES: { value: ReviewMode; label: string; desc: string; showLimit: boolean }[] = [
  { value: "STANDARD", label: "标准模式", desc: "新卡 + 到期卡，按到期时间排序", showLimit: true },
  { value: "REVIEW_ONLY", label: "仅复习", desc: "仅复习已学到期卡片", showLimit: false },
  { value: "NEW_ONLY", label: "仅新卡", desc: "仅学习新卡片，按创建时间排序", showLimit: true },
  { value: "CRAM", label: "速通", desc: "全卡组随机顺序，不限到期时间", showLimit: false },
];

export function DeckPicker({ onStart }: Props): React.ReactElement {
  const [decks, setDecks] = useState<DeckInfo[]>([]);
  const [selectedDeckId, setSelectedDeckId] = useState<string>("");
  const [selectedMode, setSelectedMode] = useState<ReviewMode>("STANDARD");
  const [limit, setLimit] = useState(20);
  const [limitInput, setLimitInput] = useState("20");
  const [loading, setLoading] = useState(true);
  const [learnedToday, setLearnedToday] = useState(0);
  const [remaining, setRemaining] = useState<number | null>(null);

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
        if (prefs.newCardDailyLimit) { setLimit(prefs.newCardDailyLimit); setLimitInput(String(prefs.newCardDailyLimit)); }
      }
    } catch {
      // ignore
    }
  };

  const selectedDeck = decks.find((d) => d.id === selectedDeckId);
  const showLimit = MODES.find((m) => m.value === selectedMode)?.showLimit ?? false;
  const modeDesc = MODES.find((m) => m.value === selectedMode)?.desc ?? "";

  const handleStart = async () => {
    if (!selectedDeck) return;

    const effectiveLimit = showLimit ? limit : 0;
    if (showLimit && learnedToday >= effectiveLimit) {
      if (selectedMode === "NEW_ONLY") {
        window.alert("今日新卡已达上限，请选择其他模式复习");
        return;
      }
      const confirmed = window.confirm(
        `今日新卡已达上限，开始复习吗？`
      );
      if (!confirmed) return;
    }

    try {
      await fetch("/api/user/preferences", {
        method: "PUT",
        credentials: "same-origin",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ lastDeckId: selectedDeck.id, lastMode: selectedMode, newCardDailyLimit: limit }),
      });
    } catch {
      // ignore
    }

    onStart(selectedDeck, selectedMode, effectiveLimit);
  };

  const fetchStatsForDeck = async (deckId: string, mode: ReviewMode) => {
    if (!deckId) return;
    try {
      const res = await fetch(
        `/api/review/stats?deckId=${deckId}&mode=${mode}`,
        { credentials: "same-origin" }
      );
      if (res.ok) {
        const data = await res.json();
        setLearnedToday(data.learnedToday ?? 0);
        setRemaining(data.remaining ?? null);
      }
    } catch {
      // ignore
    }
  };

  useEffect(() => {
    fetchStatsForDeck(selectedDeckId, selectedMode);
  }, [selectedDeckId, selectedMode]);

  if (loading) {
    return <div className={styles.container}>Loading...</div>;
  }

  return (
    <div className={styles.container}>
      <h1 className={styles.title}>Anki</h1>

      <div className={styles.section}>
        <h2 className={styles.sectionTitle}>选择牌组</h2>
        {decks.length === 0 ? (
          <p className={styles.empty}>暂无牌组，请先在管理页面创建 Deck 标签并添加卡片</p>
        ) : (
          <select
            data-testid="deck-select"
            className={styles.select}
            value={selectedDeckId}
            onChange={(e) => setSelectedDeckId(e.target.value)}
          >
            <option value="">请选择牌组</option>
            {decks.map((deck) => (
              <option key={deck.id} value={deck.id}>
                {deck.name} ({deck.cardCount}张)
              </option>
            ))}
          </select>
        )}
      </div>

      <div className={styles.section}>
        <h2 className={styles.sectionTitle}>选择模式</h2>
        <select
          data-testid="mode-select"
          className={styles.select}
          value={selectedMode}
          onChange={(e) => setSelectedMode(e.target.value as ReviewMode)}
        >
          {MODES.map((mode) => (
            <option key={mode.value} value={mode.value}>
              {mode.label}
            </option>
          ))}
        </select>
        <p className={styles.modeDescription}>{modeDesc}</p>
        {selectedDeckId && remaining !== null && (
          <p className={styles.remainingInfo} data-testid="mode-remaining">
            剩余 {remaining >= 0 ? remaining : "-"} 张
          </p>
        )}
      </div>

      {showLimit && (
        <div className={styles.section} data-testid="limit-section">
          <h2 className={styles.sectionTitle}>每日新卡上限</h2>
          <input
            type="text"
            inputMode="numeric"
            pattern="[0-9]*"
            data-testid="limit-input"
            className={styles.limitInput}
            value={limitInput}
            onChange={(e) => {
              const val = e.target.value;
              if (val === "" || /^\d*$/.test(val)) {
                setLimitInput(val);
                const num = parseInt(val, 10);
                if (val !== "" && !isNaN(num)) setLimit(num);
              }
            }}
            onBlur={() => {
              if (limitInput === "") setLimitInput(String(limit));
            }}
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
        开始练习
      </button>
    </div>
  );
}
