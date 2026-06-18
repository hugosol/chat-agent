import { useState } from "react";
import { Modal } from "../../shared/Modal";
import type { Movie } from "../../shared/types";

interface MovieDeleteModalProps {
  open: boolean;
  movie: Movie;
  onClose: () => void;
  onDeleted: () => void;
}

export function MovieDeleteModal({ open, movie, onClose, onDeleted }: MovieDeleteModalProps): JSX.Element {
  const [error, setError] = useState<string | null>(null);
  const [deleting, setDeleting] = useState(false);

  const lineCount = movie.subtitleLineCount ?? 0;
  const yearText = movie.year ? ` (${movie.year})` : "";
  const subtitleWarning = lineCount > 0
    ? `该电影的所有字幕数据（${lineCount.toLocaleString()} 行）将被一并删除。`
    : "该电影暂无字幕数据。";

  const handleDelete = async () => {
    setDeleting(true);
    setError(null);
    try {
      const resp = await fetch(`/api/movies/${movie.imdbId}`, { method: "DELETE" });
      if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
      onDeleted();
    } catch (e) {
      setError(e instanceof Error ? e.message : "删除失败");
    } finally {
      setDeleting(false);
    }
  };

  return (
    <Modal open={open} title="删除电影" onClose={onClose} danger={true} saveLabel="删除" onSave={handleDelete}>
      <div data-testid="movie-delete-modal">
        <p data-testid="movie-delete-text">
          确定删除 {movie.title}{yearText}？{subtitleWarning}
        </p>
        {error && (
          <div data-testid="movie-delete-error" style={{ color: "rgb(231, 76, 60)", marginTop: 8 }}>
            {error}
          </div>
        )}
      </div>
    </Modal>
  );
}
