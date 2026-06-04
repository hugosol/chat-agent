import React, { useState, useEffect, useCallback } from "react";
import { CardDisplay } from "./CardDisplay";
import { RatingButtons, RatingValue } from "./RatingButtons";
import { StatsBar } from "./StatsBar";
import type { DeckInfo, ReviewMode, ReviewCard, ReviewStats, RateCardResponse, NextCardResponse } from "./reviewTypes";
import styles from "./ReviewPage.module.css";

interface Props {
  deck: DeckInfo;
  mode: ReviewMode;
  limit: number;
  onComplete: (stats: ReviewStats, lastCard: ReviewCard | null) => void;
  onBack: () => void;
}

export function ReviewPage({ deck, mode, limit, onComplete, onBack }: Props): React.ReactElement {
  const [card, setCard] = useState<ReviewCard | null>(null);
  const [flipped, setFlipped] = useState(false);
  const [stats, setStats] = useState<ReviewStats>({
    reviewedToday: 0, remaining: 0, learnedToday: 0, dailyLimit: limit, nextDueAt: null,
  });
  const [loading, setLoading] = useState(true);
  const [rating, setRating] = useState(false);

  const loadNextCard = useCallback(async () => {
    try {
      const res = await fetch(
        `/api/review/next?deckId=${deck.id}&mode=${mode}`,
        { credentials: "same-origin" }
      );
      if (res.ok) {
        const data: NextCardResponse = await res.json();
        setStats(data.stats);
        if (data.card) {
          setCard(data.card);
          setFlipped(false);
        } else {
          onComplete(data.stats, card);
        }
      }
    } finally {
      setLoading(false);
    }
  }, [deck.id, mode, limit]);

  useEffect(() => {
    loadNextCard();
  }, []);

  const handleFlip = () => {
    setFlipped(true);
  };

  const handleRate = async (ratingValue: RatingValue) => {
    if (!card || rating) return;
    setRating(true);

    try {
      const res = await fetch("/api/review/rate", {
        method: "POST",
        credentials: "same-origin",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ cardId: card.id, rating: ratingValue, deckId: deck.id, mode }),
      });
      if (res.ok) {
        const data: RateCardResponse = await res.json();
        setStats(data.stats);
        if (data.nextCard) {
          setCard(data.nextCard);
          setFlipped(false);
        } else {
          onComplete(data.stats, data.card);
        }
      }
    } finally {
      setRating(false);
    }
  };

  if (loading) {
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

      <div className={styles.content} onClick={!flipped ? handleFlip : undefined}>
        <CardDisplay key={card.id} front={card.front} back={card.back} />
      </div>

      {flipped && (
        <RatingButtons onRate={handleRate} disabled={rating} />
      )}

      <StatsBar stats={stats} />
    </div>
  );
}
