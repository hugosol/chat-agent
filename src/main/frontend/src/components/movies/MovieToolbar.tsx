import { useState, useEffect, useRef, useCallback } from "react";
import { DropdownMenu } from "../manage/DropdownMenu";
import type { DropdownItem } from "../manage/DropdownMenu";
import styles from "./MovieToolbar.module.css";

interface MovieToolbarProps {
  search: string;
  sort: string;
  onSearchChange: (value: string) => void;
  onSortChange: (value: string) => void;
  onAddMovie: () => void;
  onImportMovies: () => void;
}

const SORT_OPTIONS: { value: string; label: string }[] = [
  { value: "title,asc", label: "名称 A→Z" },
  { value: "title,desc", label: "名称 Z→A" },
  { value: "releaseYear,asc", label: "年份 ↑" },
  { value: "releaseYear,desc", label: "年份 ↓" },
  { value: "createTime,asc", label: "添加时间 ↑" },
  { value: "createTime,desc", label: "添加时间 ↓" },
];

const SORT_ITEMS: DropdownItem[] = SORT_OPTIONS.map((opt) => ({
  label: opt.label,
  value: opt.value,
  onClick: () => {},
}));

export function MovieToolbar({
  search,
  sort,
  onSearchChange,
  onSortChange,
  onAddMovie,
  onImportMovies,
}: MovieToolbarProps): JSX.Element {
  const [localSearch, setLocalSearch] = useState(search);
  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    setLocalSearch(search);
  }, [search]);

  const handleSearchInput = (value: string) => {
    setLocalSearch(value);
    if (debounceRef.current) clearTimeout(debounceRef.current);
    debounceRef.current = setTimeout(() => {
      onSearchChange(value);
    }, 300);
  };

  const handleSortSelect = useCallback(
    (value: string) => {
      onSortChange(value);
    },
    [onSortChange]
  );

  return (
    <div className={styles.toolbar} data-testid="movies-toolbar">
      <div className={styles.toolbarRow}>
        <input
          type="text"
          className={styles.searchInput}
          data-testid="movies-search-input"
          placeholder="搜索电影标题..."
          value={localSearch}
          onChange={(e) => handleSearchInput(e.target.value)}
        />
        <div className={styles.actions}>
          <DropdownMenu
            label="名称 A→Z"
            items={SORT_ITEMS.map((item) => ({
              ...item,
              onClick: handleSortSelect,
            }))}
            selectedValue={sort}
            testId="movies-sort-btn"
            optionTestId="movies-sort-option"
          />
          <button
            className={styles.iconBtn}
            data-testid="movies-import-btn"
            title="批量导入"
            aria-label="批量导入"
            onClick={onImportMovies}
          >
            {"\uD83D\uDCC4"}
          </button>
          <button
            className={styles.createBtn}
            data-testid="movies-add-btn"
            title="添加电影"
            aria-label="添加电影"
            onClick={onAddMovie}
          >
            +
          </button>
        </div>
      </div>
    </div>
  );
}
