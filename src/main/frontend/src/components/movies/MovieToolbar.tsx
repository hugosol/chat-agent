import { useState, useEffect, useRef } from "react";
import styles from "./MovieToolbar.module.css";

interface MovieToolbarProps {
  search: string;
  sort: string;
  onSearchChange: (value: string) => void;
  onSortChange: (value: string) => void;
  onAddMovie: () => void;
  onImportMovies: () => void;
}

const SORT_OPTIONS = [
  { value: "title,asc", label: "名称 A→Z" },
  { value: "title,desc", label: "名称 Z→A" },
  { value: "releaseYear,asc", label: "年份 ↑" },
  { value: "releaseYear,desc", label: "年份 ↓" },
  { value: "createTime,asc", label: "添加时间 ↑" },
  { value: "createTime,desc", label: "添加时间 ↓" },
];

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

  // Sync external search value when it changes (e.g., reset from parent)
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

  return (
    <div className={styles.toolbar} data-testid="movies-toolbar">
      <input
        type="text"
        className={styles.searchInput}
        data-testid="movies-search-input"
        placeholder="搜索电影标题..."
        value={localSearch}
        onChange={(e) => handleSearchInput(e.target.value)}
      />
      <select
        className={styles.sortSelect}
        data-testid="movies-sort-select"
        value={sort}
        onChange={(e) => onSortChange(e.target.value)}
      >
        {SORT_OPTIONS.map((opt) => (
          <option key={opt.value} value={opt.value}>
            {opt.label}
          </option>
        ))}
      </select>
      <button
        className="btn btn-primary"
        data-testid="movies-add-btn"
        onClick={onAddMovie}
      >
        添加电影
      </button>
      <button
        className="btn"
        data-testid="movies-import-btn"
        onClick={onImportMovies}
      >
        批量导入
      </button>
    </div>
  );
}
