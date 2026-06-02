import { useState, useCallback, useRef, useEffect } from "react";
import type { Tag } from "../../shared/types";
import styles from "./CardToolbar.module.css";

interface CardToolbarProps {
  search: string;
  sort: string;
  deckId: number | null;
  decks: Tag[];
  onSearchChange: (search: string) => void;
  onSortChange: (sort: string) => void;
  onDeckChange: (deckId: string | null) => void;
  onCreate: () => void;
}

function CardToolbar({
  search,
  sort,
  deckId,
  decks,
  onSearchChange,
  onSortChange,
  onDeckChange,
  onCreate,
}: CardToolbarProps): JSX.Element {
  const [localSearch, setLocalSearch] = useState(search);
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    setLocalSearch(search);
  }, [search]);

  const handleSearchChange = useCallback(
    (e: React.ChangeEvent<HTMLInputElement>) => {
      const value = e.target.value;
      setLocalSearch(value);
      if (timerRef.current) clearTimeout(timerRef.current);
      timerRef.current = setTimeout(() => {
        onSearchChange(value);
      }, 300);
    },
    [onSearchChange]
  );

  const handleSortClick = useCallback(
    (field: string) => {
      if (sort.startsWith(field)) {
        const dir = sort.endsWith(",asc") ? ",desc" : ",asc";
        onSortChange(field + dir);
      } else {
        onSortChange(field + (field === "front" ? ",asc" : ",desc"));
      }
    },
    [sort, onSortChange]
  );

  const nameArrow = sort.startsWith("front") ? (sort.endsWith(",asc") ? " ↑" : " ↓") : "";
  const timeArrow = sort.startsWith("createTime") ? (sort.endsWith(",asc") ? " ↑" : " ↓") : "";

  return (
    <div className={styles.toolbar}>
      <div className={styles.searchRow}>
        <input
          type="text"
          className={styles.searchInput}
          data-testid="card-search"
          placeholder="搜索卡片..."
          value={localSearch}
          onChange={handleSearchChange}
        />
        <div className={styles.sortBtns}>
          <button
            className={`${styles.sortBtn}${sort.startsWith("front") ? " " + styles.activeSort : ""}`}
            data-testid="sort-btn-name"
            data-active={sort.startsWith("front") ? "true" : "false"}
            onClick={() => handleSortClick("front")}
          >
            A&rarr;Z{nameArrow}
          </button>
          <button
            className={`${styles.sortBtn}${sort.startsWith("createTime") ? " " + styles.activeSort : ""}`}
            data-testid="sort-btn-time"
            data-active={sort.startsWith("createTime") ? "true" : "false"}
            onClick={() => handleSortClick("createTime")}
          >
            Time{timeArrow}
          </button>
        </div>
      </div>
      <div className={styles.deckChips}>
        {decks.length === 0 ? (
          <span style={{ color: "#555", fontSize: "0.75em" }}>暂无牌组，创建牌组</span>
        ) : (
          decks.map((deck) => (
            <span
              key={deck.id}
              className={`${styles.deckChip}${deckId === deck.id ? " " + styles.activeDeck : ""}`}
              data-testid="deck-chip"
              data-active={deckId === deck.id ? "true" : "false"}
              onClick={() => onDeckChange(deckId === deck.id ? null : String(deck.id))}
            >
              {deck.name}
            </span>
          ))
        )}
      </div>
      <button className="btn btn-primary" onClick={onCreate}>
        + 创建卡片
      </button>
    </div>
  );
}

export { CardToolbar };
export type { CardToolbarProps };
