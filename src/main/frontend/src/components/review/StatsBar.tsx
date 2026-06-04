import React from "react";
import type { ReviewStats } from "./reviewTypes";
import styles from "./StatsBar.module.css";

interface Props {
  stats: ReviewStats;
}

export function StatsBar({ stats }: Props): React.ReactElement {
  return (
    <div className={styles.container} data-testid="stats-bar">
      <span data-testid="stats-reviewed">已复习 {stats.reviewedToday} 张</span>
      <span className={styles.separator}>|</span>
      <span data-testid="stats-remaining">剩余 {stats.remaining} 张</span>
      <span className={styles.separator}>|</span>
      <span data-testid="stats-new">新卡 {stats.learnedToday}/{stats.dailyLimit}</span>
    </div>
  );
}
