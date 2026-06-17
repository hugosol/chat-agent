import { useState, useEffect, useCallback } from "react";
import { Header } from "../Header/Header";
import { MovieToolbar } from "./MovieToolbar";
import { MovieList } from "./MovieList";
import { MovieSearchModal } from "./MovieSearchModal";
import { MovieImportModal } from "./MovieImportModal";
import { MovieDeleteModal } from "./MovieDeleteModal";
import { MovieRetryModal } from "./MovieRetryModal";
import type { Movie, PageResponse } from "../../shared/types";
import styles from "./MoviesApp.module.css";

export function MoviesApp(): JSX.Element {
  const [movies, setMovies] = useState<Movie[]>([]);
  const [search, setSearch] = useState("");
  const [sort, setSort] = useState("title,asc");
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Modal states
  const [searchModalOpen, setSearchModalOpen] = useState(false);
  const [importModalOpen, setImportModalOpen] = useState(false);
  const [deleteTarget, setDeleteTarget] = useState<Movie | null>(null);
  const [retryTarget, setRetryTarget] = useState<Movie | null>(null);

  const fetchMovies = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const params = new URLSearchParams();
      if (search) params.set("search", search);
      params.set("sort", sort);
      params.set("page", String(page));
      params.set("size", "10");

      const resp = await fetch(`/api/movies?${params.toString()}`);
      if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
      const data: PageResponse<Movie> = await resp.json();
      setMovies(data.content);
      setTotalPages(data.totalPages);
    } catch (e) {
      setError(e instanceof Error ? e.message : "加载失败");
    } finally {
      setLoading(false);
    }
  }, [search, sort, page]);

  useEffect(() => {
    fetchMovies();
  }, [fetchMovies]);

  const handleSearchChange = useCallback((value: string) => {
    setSearch(value);
    setPage(0);
  }, []);

  const handleSortChange = useCallback((value: string) => {
    setSort(value);
    setPage(0);
  }, []);

  const handlePageChange = useCallback((newPage: number) => {
    setPage(newPage);
  }, []);

  const handleAdded = useCallback(() => {
    setSearchModalOpen(false);
    setSearch("");
    setPage(0);
  }, []);

  const handleImported = useCallback(() => {
    setImportModalOpen(false);
    setSearch("");
    setPage(0);
  }, []);

  const handleDeleted = useCallback(() => {
    setDeleteTarget(null);
    fetchMovies();
  }, [fetchMovies]);

  const handleRetried = useCallback(() => {
    setRetryTarget(null);
    fetchMovies();
  }, [fetchMovies]);

  return (
    <div className={styles.app} data-testid="movies-page">
      <Header />
      <MovieToolbar
        search={search}
        sort={sort}
        onSearchChange={handleSearchChange}
        onSortChange={handleSortChange}
        onAddMovie={() => setSearchModalOpen(true)}
        onImportMovies={() => setImportModalOpen(true)}
      />
      {error && <div data-testid="movies-error">{error}</div>}
      <MovieList
        movies={movies}
        loading={loading}
        page={page}
        totalPages={totalPages}
        onPageChange={handlePageChange}
        onDelete={setDeleteTarget}
        onRetry={setRetryTarget}
      />
      <MovieSearchModal
        open={searchModalOpen}
        onClose={() => setSearchModalOpen(false)}
        onAdded={handleAdded}
      />
      <MovieImportModal
        open={importModalOpen}
        onClose={() => setImportModalOpen(false)}
        onImported={handleImported}
      />
      {deleteTarget && (
        <MovieDeleteModal
          open={true}
          movie={deleteTarget}
          onClose={() => setDeleteTarget(null)}
          onDeleted={handleDeleted}
        />
      )}
      {retryTarget && (
        <MovieRetryModal
          open={true}
          movie={retryTarget}
          onClose={() => setRetryTarget(null)}
          onRetried={handleRetried}
        />
      )}
    </div>
  );
}
