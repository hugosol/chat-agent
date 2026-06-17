import type { Movie } from "../../shared/types";
import styles from "./MovieBlock.module.css";

interface MovieBlockProps {
  movie: Movie;
  onDelete: (movie: Movie) => void;
  onRetry: (movie: Movie) => void;
}

function statusIcon(status: string): string {
  switch (status) {
    case "PENDING": return "\u23F3";
    case "DOWNLOADING": return "\u23EC";
    case "DONE": return "\u2713";
    case "FAILED": return "\u2717";
    default: return "?";
  }
}

function statusLabel(movie: Movie): string {
  switch (movie.subtitleStatus) {
    case "PENDING": return "等待下载";
    case "DOWNLOADING": return "下载中";
    case "DONE": return `${movie.subtitleLineCount?.toLocaleString() ?? 0} 行`;
    case "FAILED": return movie.subtitleError ?? "下载失败";
    default: return movie.subtitleStatus;
  }
}

function statusClassName(status: string): string {
  if (status === "DONE") return styles.statusDone;
  if (status === "FAILED") return styles.statusFailed;
  return styles.statusPending;
}

export function MovieBlock({ movie, onDelete, onRetry }: MovieBlockProps): JSX.Element {
  const canDownload = movie.subtitleStatus === "PENDING" || movie.subtitleStatus === "FAILED";
  const downloadLabel = movie.subtitleStatus === "FAILED" ? "重试" : "下载字幕";

  return (
    <div className={styles.block} data-testid="movie-block">
      <div className={styles.info}>
        <span className={styles.title} data-testid="movie-title">
          {movie.title}
        </span>
        {movie.year && (
          <span className={styles.year} data-testid="movie-year">
            ({movie.year})
          </span>
        )}
      </div>
      <div className={styles.statusRow}>
        <span
          className={`${styles.status} ${statusClassName(movie.subtitleStatus)}`}
          data-testid="movie-status"
        >
          {statusIcon(movie.subtitleStatus)} {statusLabel(movie)}
        </span>
      </div>
      <div className={styles.actions}>
        {canDownload && (
          <button
            className={`btn ${styles.actionBtn}`}
            data-testid="movie-download-btn"
            onClick={() => {
              console.log("[MovieBlock] download clicked:", movie.imdbId, movie.title, movie.subtitleStatus);
              onRetry(movie);
            }}
          >
            {downloadLabel}
          </button>
        )}
        <button
          className={`btn ${styles.actionBtn}`}
          data-testid="movie-delete-btn"
          onClick={() => onDelete(movie)}
        >
          删除
        </button>
      </div>
    </div>
  );
}
