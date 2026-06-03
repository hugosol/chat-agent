import { useState, useCallback, useRef, useEffect } from "react";
import type { Tag } from "../../shared/types";
import styles from "./CardToolbar.module.css";

interface CardToolbarProps {
  search: string;
  sort: string;
  deckId: string | null;
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
      }, 1000);
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

  const hasDecks = decks.length > 0;

  return (
    <div className={styles.toolbar}>
      {hasDecks && (
        <div className={styles.deckTabs}>
          <span
            className={`${styles.deckTab}${deckId === null ? " " + styles.activeTab : ""}`}
            data-testid="deck-tab"
            data-active={deckId === null ? "true" : "false"}
            onClick={() => onDeckChange(null)}
          >
            全部
          </span>
          {decks.map((deck) => (
            <span
              key={deck.id}
              className={`${styles.deckTab}${deckId === deck.id ? " " + styles.activeTab : ""}`}
              data-testid="deck-tab"
              data-active={deckId === deck.id ? "true" : "false"}
              onClick={() => onDeckChange(deck.id)}
            >
              {deck.name}
            </span>
          ))}
        </div>
      )}
      <div className={styles.toolbarRow}>
        <input
          type="text"
          className={styles.searchInput}
          data-testid="card-search"
          placeholder="搜索卡片..."
          value={localSearch}
          onChange={handleSearchChange}
        />
        <div className={styles.actions}>
          <div className={styles.sortBtns}>
            <button
              className={`${styles.sortBtn}${sort.startsWith("front") ? " " + styles.activeSort : ""}`}
              data-testid="sort-btn-name"
              data-active={sort.startsWith("front") ? "true" : "false"}
              onClick={() => handleSortClick("front")}
            >
              Aa{nameArrow}
            </button>
            <button
              className={`${styles.sortBtn}${sort.startsWith("createTime") ? " " + styles.activeSort : ""}`}
              data-testid="sort-btn-time"
              data-active={sort.startsWith("createTime") ? "true" : "false"}
              onClick={() => handleSortClick("createTime")}
            >
              T{timeArrow}
            </button>
          </div>
          <button className={styles.createBtn} onClick={onCreate}>
            +
          </button>
        </div>
      </div>
    </div>
  );
}

export { CardToolbar };
export type { CardToolbarProps };
