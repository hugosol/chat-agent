import { useState } from "react";
import { Modal } from "../../shared/Modal";
import type { Movie } from "../../shared/types";

interface MovieRetryModalProps {
  open: boolean;
  movie: Movie;
  onClose: () => void;
  onRetried: () => void;
}

export function MovieRetryModal({ open, movie, onClose, onRetried }: MovieRetryModalProps): JSX.Element {
  const [error, setError] = useState<string | null>(null);
  const [retrying, setRetrying] = useState(false);

  const handleRetry = async () => {
    console.log("[MovieRetryModal] triggering download for:", movie.imdbId, movie.title);
    setRetrying(true);
    setError(null);
    try {
      const resp = await fetch(`/api/movies/${movie.imdbId}/download`, { method: "POST" });
      console.log("[MovieRetryModal] response status:", resp.status);
      if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
      console.log("[MovieRetryModal] download triggered successfully");
      onRetried();
    } catch (e) {
      console.error("[MovieRetryModal] download failed:", e);
      setError(e instanceof Error ? e.message : "重试失败");
    } finally {
      setRetrying(false);
    }
  };

  return (
    <Modal open={open} title="重新下载字幕" onClose={onClose} saveLabel="确认" saveDisabled={retrying} onSave={handleRetry}>
      <div data-testid="movie-retry-modal">
        <p data-testid="movie-retry-text">
          重新下载 "{movie.title}" 的字幕？将清除现有字幕数据并重新获取。
        </p>
        {retrying && (
          <div data-testid="movie-retry-loading" style={{ marginTop: 8, display: "flex", alignItems: "center", gap: 8 }}>
            <span className="spinner" />
            <span>下载中...</span>
          </div>
        )}
        {error && (
          <div data-testid="movie-retry-error" style={{ color: "rgb(231, 76, 60)", marginTop: 8 }}>
            {error}
          </div>
        )}
      </div>
    </Modal>
  );
}
