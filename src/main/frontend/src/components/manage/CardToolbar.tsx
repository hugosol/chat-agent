import { useState, useCallback, useRef, useEffect } from "react";
import type { Tag } from "../../shared/types";
import { DropdownMenu } from "./DropdownMenu";
import type { DropdownItem } from "./DropdownMenu";
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
  onBatchOpen: (mode: "import" | "export") => void;
}

const SORT_ITEMS: DropdownItem[] = [
  { label: "Aa ↑", value: "front,asc", onClick: () => {} },
  { label: "Aa ↓", value: "front,desc", onClick: () => {} },
  { label: "T ↑", value: "createTime,asc", onClick: () => {} },
  { label: "T ↓", value: "createTime,desc", onClick: () => {} },
];

const BATCH_ITEMS: DropdownItem[] = [
  { label: "导出", value: "export", onClick: () => {} },
  { label: "导入", value: "import", onClick: () => {} },
];

function CardToolbar({
  search,
  sort,
  deckId,
  decks,
  onSearchChange,
  onSortChange,
  onDeckChange,
  onCreate,
  onBatchOpen,
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

  const handleSortSelect = useCallback(
    (value: string) => {
      onSortChange(value);
    },
    [onSortChange]
  );

  const handleBatchSelect = useCallback(
    (value: string) => {
      onBatchOpen(value as "import" | "export");
    },
    [onBatchOpen]
  );

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
          <DropdownMenu
            label="Aa ↑"
            items={SORT_ITEMS.map((item) => ({
              ...item,
              onClick: handleSortSelect,
            }))}
            selectedValue={sort}
            testId="sort-dropdown-btn"
            optionTestId="sort-option"
          />
          <DropdownMenu
            label="📄"
            items={BATCH_ITEMS.map((item) => ({
              ...item,
              onClick: handleBatchSelect,
            }))}
            selectedValue=""
            testId="batch-dropdown-btn"
            optionTestId="batch-option"
          />
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
