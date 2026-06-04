import React from "react";
import type { ReviewStats, ReviewCard } from "./reviewTypes";
import styles from "./CompletePage.module.css";

interface Props {
  stats: ReviewStats;
  lastCard: ReviewCard | null;
  onBack: () => void;
}

export function CompletePage({ stats, lastCard, onBack }: Props): React.ReactElement {
  const hoursUntilNext = stats.nextDueAt
    ? Math.round((new Date(stats.nextDueAt).getTime() - Date.now()) / 3600000)
    : null;

  return (
    <div className={styles.container} data-testid="complete-page">
      <div className={styles.card}>
        <h1 className={styles.title}>本轮复习完成</h1>
        <div className={styles.stats}>
          <div className={styles.statItem}>
            <span className={styles.statValue}>{stats.reviewedToday}</span>
            <span className={styles.statLabel}>复习张数</span>
          </div>
          <div className={styles.statItem}>
            <span className={styles.statValue}>{stats.learnedToday}</span>
            <span className={styles.statLabel}>新学张数</span>
          </div>
        </div>
        {hoursUntilNext !== null && hoursUntilNext > 0 && (
          <p className={styles.nextDue} data-testid="complete-next-due">
            下一张卡片将在约 {hoursUntilNext} 小时后到期
          </p>
        )}
        <button
          data-testid="complete-back-btn"
          className={styles.backBtn}
          onClick={onBack}
        >
          返回 Deck 选择
        </button>
      </div>
    </div>
  );
}
