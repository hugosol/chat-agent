import { useState, useEffect, useRef } from "react";
import { Modal } from "../../shared/Modal";

interface MovieCandidate {
  imdbId: string;
  title: string;
  year: number | null;
}

interface MovieSearchModalProps {
  open: boolean;
  onClose: () => void;
  onAdded: () => void;
}

export function MovieSearchModal({ open, onClose, onAdded }: MovieSearchModalProps): JSX.Element {
  const [query, setQuery] = useState("");
  const [candidates, setCandidates] = useState<MovieCandidate[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [addingId, setAddingId] = useState<string | null>(null);
  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    if (!open) {
      setQuery("");
      setCandidates([]);
      setError(null);
      setAddingId(null);
    }
  }, [open]);

  const handleSearch = (value: string) => {
    setQuery(value);
    if (debounceRef.current) clearTimeout(debounceRef.current);
    if (!value.trim()) {
      setCandidates([]);
      return;
    }
    debounceRef.current = setTimeout(async () => {
      setLoading(true);
      setError(null);
      try {
        const resp = await fetch("/api/movies/search", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ query: value }),
        });
        if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
        const data: MovieCandidate[] = await resp.json();
        setCandidates(data);
      } catch (e) {
        setError(e instanceof Error ? e.message : "搜索失败");
      } finally {
        setLoading(false);
      }
    }, 300);
  };

  const handleAdd = async (candidate: MovieCandidate) => {
    setAddingId(candidate.imdbId);
    setError(null);
    try {
      const resp = await fetch("/api/movies", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          imdbId: candidate.imdbId,
          title: candidate.title,
          year: candidate.year,
        }),
      });
      if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
      onAdded();
    } catch (e) {
      setError(e instanceof Error ? e.message : "添加失败");
    } finally {
      setAddingId(null);
    }
  };

  return (
    <Modal open={open} title="添加电影" onClose={onClose}>
      <div data-testid="movie-search-modal">
        <input
          type="text"
          className="modal-input"
          data-testid="movie-search-query"
          placeholder="输入电影名称搜索 TMDB..."
          value={query}
          onChange={(e) => handleSearch(e.target.value)}
          autoFocus
        />
        {loading && <div data-testid="movie-search-loading">搜索中...</div>}
        {error && <div data-testid="movie-search-error" style={{ color: "rgb(231, 76, 60)" }}>{error}</div>}
        {!loading && !error && query.trim() && candidates.length === 0 && (
          <div data-testid="movie-search-empty">未找到匹配的电影</div>
        )}
        {candidates.length > 0 && (
          <div data-testid="movie-search-results" style={{ marginTop: 8 }}>
            {candidates.map((c) => (
              <div
                key={c.imdbId}
                data-testid="movie-search-candidate"
                style={{
                  display: "flex",
                  alignItems: "center",
                  justifyContent: "space-between",
                  padding: "8px 0",
                  borderBottom: "1px solid #1a3a5c",
                }}
              >
                <div>
                  <span style={{ color: "#e0e0e0" }}>{c.title}</span>
                  {c.year && <span style={{ color: "#888", marginLeft: 8 }}>({c.year})</span>}
                  <div style={{ fontSize: "0.75em", color: "#666" }}>{c.imdbId}</div>
                </div>
                <button
                  className="btn btn-primary"
                  data-testid="movie-search-add-btn"
                  disabled={addingId === c.imdbId}
                  onClick={() => handleAdd(c)}
                >
                  {addingId === c.imdbId ? "添加中..." : "添加"}
                </button>
              </div>
            ))}
          </div>
        )}
      </div>
    </Modal>
  );
}
