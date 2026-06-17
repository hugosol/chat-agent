import React, { useState, useEffect, useCallback } from "react";
import { CardDisplay } from "./CardDisplay";
import { RatingButtons, RatingValue } from "./RatingButtons";
import { StatsBar } from "./StatsBar";
import type { DeckInfo, ReviewMode, ReviewCard, ReviewStats, CardResponse, PreviewInfo } from "./reviewTypes";
import styles from "./ReviewPage.module.css";

type PageState = "start" | "review" | "submit";

interface Props {
  deck: DeckInfo;
  mode: ReviewMode;
  limit: number;
  onComplete: (stats: ReviewStats) => void;
  onBack: () => void;
}

export function ReviewPage({ deck, mode, limit, onComplete, onBack }: Props): React.ReactElement {
  const [card, setCard] = useState<ReviewCard | null>(null);
  const [flipped, setFlipped] = useState(false);
  const [preview, setPreview] = useState<PreviewInfo | null>(null);
  const [editing, setEditing] = useState(false);
  const [stats, setStats] = useState<ReviewStats>({
    reviewedToday: 0, remaining: 0, learnedToday: 0, dailyLimit: limit, nextDueAt: null,
  });
  const [pageState, setPageState] = useState<PageState>("start");

  const loadFirstCard = useCallback(async () => {
    try {
      const res = await fetch(
        `/api/review/start?deckId=${deck.id}&mode=${mode}`,
        { credentials: "same-origin" }
      );
      if (res.ok) {
        const data: CardResponse = await res.json();
        setStats(data.stats);
        if (data.card) {
          setCard(data.card);
          setFlipped(false);
          setPreview(data.preview ?? null);
          setPageState("review");
        } else {
          onComplete(data.stats);
        }
      }
    } catch {
      setPageState("review");
    }
  }, [deck.id, mode, limit]);

  useEffect(() => {
    loadFirstCard();
  }, []);

  const handleFlip = () => {
    setFlipped(true);
  };

  const handleNextCard = async (ratingValue: RatingValue) => {
    if (!card || pageState === "submit") return;
    setPageState("submit");

    try {
      const res = await fetch("/api/review/next", {
        method: "POST",
        credentials: "same-origin",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ cardId: card.id, rating: ratingValue, deckId: deck.id, mode }),
      });
      if (res.ok) {
        const data: CardResponse = await res.json();
        setStats(data.stats);
        if (data.card) {
          setCard(data.card);
          setFlipped(false);
          setPreview(data.preview ?? null);
          setPageState("review");
        } else {
          onComplete(data.stats);
        }
      }
    } finally {
      if (pageState === "submit") {
        setPageState("review");
      }
    }
  };

  const handleCardUpdated = (updated: ReviewCard) => {
    setCard(updated);
  };

  if (pageState === "start") {
    return <div className={styles.container}>Loading...</div>;
  }

  if (!card) {
    return <div className={styles.container}>No cards available</div>;
  }

  return (
    <div className={styles.container}>
      <div className={styles.topBar}>
        <button
          data-testid="topbar-back"
          className={styles.backBtn}
          onClick={onBack}
        >
          &larr; 返回
        </button>
        <span className={styles.deckName} data-testid="topbar-deck-name">
          {deck.name}
        </span>
      </div>

      <div className={styles.content}>
        <CardDisplay
          key={card.id}
          front={card.front}
          back={card.back}
          cardId={card.id}
          flipped={flipped}
          enhancement={card.enhancement}
          onFlip={handleFlip}
          onCardUpdated={handleCardUpdated}
          onEditingChange={setEditing}
        />
      </div>

      {flipped && (
        <RatingButtons onRate={handleNextCard} disabled={pageState === "submit" || editing} preview={preview} />
      )}

      <StatsBar stats={stats} />
    </div>
  );
}
