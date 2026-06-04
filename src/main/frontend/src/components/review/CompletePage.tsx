import React from "react";
import type { ReviewStats, ReviewCard } from "./reviewTypes";
import styles from "./CompletePage.module.css";

interface Props {
  stats: ReviewStats;
  lastCard: ReviewCard | null;
  onBack: () => void;
}

export function CompletePage({ stats, lastCard, onBack }: Props): React.ReactElement {
  const diffMs = stats.nextDueAt
    ? new Date(stats.nextDueAt).getTime() - Date.now()
    : null;

  const formatNextDue = (): string | null => {
    if (diffMs === null) return null;
    if (diffMs <= 0) return "即将到期";
    if (diffMs < 60000) {
      const seconds = Math.round(diffMs / 1000);
      return `下一张卡片将在约 ${seconds} 秒后到期`;
    }
    const minutes = Math.round(diffMs / 60000);
    if (minutes < 60) {
      return `下一张卡片将在约 ${minutes} 分钟后到期`;
    }
    const hours = Math.round(diffMs / 3600000);
    return `下一张卡片将在约 ${hours} 小时后到期`;
  };

  const nextDueText = formatNextDue();

  return (
    <div className={styles.container} data-testid="complete-page">
      <div className={styles.card}>
        <h1 className={styles.title}>本轮复习完成</h1>
        <div className={styles.stats}>
          <div className={styles.statItem}>
            <span className={styles.statValue}>{stats.reviewedToday}</span>
            <span className={styles.statLabel}>今日复习</span>
          </div>
          <div className={styles.statItem}>
            <span className={styles.statValue}>{stats.learnedToday >= 0 ? stats.learnedToday : "-"}</span>
            <span className={styles.statLabel}>今日新学</span>
          </div>
        </div>
        {nextDueText && (
          <p className={styles.nextDue} data-testid="complete-next-due">
            {nextDueText}
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
