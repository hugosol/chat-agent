import React from "react";
import { formatInterval } from "../../shared/utils";
import type { PreviewInfo } from "./reviewTypes";
import styles from "./RatingButtons.module.css";

export type RatingValue = "AGAIN" | "HARD" | "GOOD" | "EASY";

interface Props {
  onRate: (rating: RatingValue) => void;
  disabled?: boolean;
  preview?: PreviewInfo | null;
}

const BUTTONS: { rating: RatingValue; label: string; className: string; testId: string }[] = [
  { rating: "AGAIN", label: "Again", className: styles.ratingAgain, testId: "rating-again" },
  { rating: "HARD", label: "Hard", className: styles.ratingHard, testId: "rating-hard" },
  { rating: "GOOD", label: "Good", className: styles.ratingGood, testId: "rating-good" },
  { rating: "EASY", label: "Easy", className: styles.ratingEasy, testId: "rating-easy" },
];

export function RatingButtons({ onRate, disabled, preview }: Props): React.ReactElement {
  const now = new Date();

  return (
    <div className={styles.container}>
      {BUTTONS.map((btn) => {
        let text = btn.label;
        if (preview) {
          const info = preview[btn.rating];
          if (info) {
            const interval = formatInterval(info.due, now);
            text = `${btn.label} · ${interval}`;
          }
        }
        return (
          <button
            key={btn.rating}
            data-testid={btn.testId}
            className={`${styles.btn} ${btn.className}`}
            disabled={disabled}
            onClick={() => onRate(btn.rating)}
          >
            {text}
          </button>
        );
      })}
    </div>
  );
}
