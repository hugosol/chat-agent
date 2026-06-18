import { Pagination } from "../../shared/Pagination";
import { MovieBlock } from "./MovieBlock";
import type { Movie } from "../../shared/types";
import styles from "./MovieList.module.css";

interface MovieListProps {
  movies: Movie[];
  loading: boolean;
  page: number;
  totalPages: number;
  onPageChange: (page: number) => void;
  onDelete: (movie: Movie) => void;
  onRetry: (movie: Movie) => void;
}

export function MovieList({
  movies,
  loading,
  page,
  totalPages,
  onPageChange,
  onDelete,
  onRetry,
}: MovieListProps): JSX.Element {
  if (loading) {
    return <div className={styles.loading} data-testid="movies-loading">加载中...</div>;
  }

  if (movies.length === 0) {
    return (
      <div className={styles.empty} data-testid="movies-empty">
        暂无电影
      </div>
    );
  }

  return (
    <div className={styles.listContainer}>
      <div className={styles.list} data-testid="movies-list">
        {movies.map((movie) => (
          <MovieBlock
            key={movie.id}
            movie={movie}
            onDelete={onDelete}
            onRetry={onRetry}
          />
        ))}
      </div>
      <Pagination page={page} totalPages={totalPages} onPageChange={onPageChange} />
    </div>
  );
}
