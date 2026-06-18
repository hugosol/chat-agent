import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, fireEvent, screen, waitFor } from "@testing-library/react";
import { MoviesApp } from "../../components/movies/MoviesApp";
import type { Movie, PageResponse } from "../../shared/types";

const mockMovies: Movie[] = [
  {
    id: "1", imdbId: "tt001", title: "Inception", year: 2010,
    subtitleStatus: "DONE", subtitleLineCount: 3421, subtitleError: null,
    createTime: "2024-01-01T00:00:00Z",
  },
  {
    id: "2", imdbId: "tt002", title: "The Matrix", year: 1999,
    subtitleStatus: "PENDING", subtitleLineCount: 0, subtitleError: null,
    createTime: "2024-01-02T00:00:00Z",
  },
  {
    id: "3", imdbId: "tt003", title: "Interstellar", year: 2014,
    subtitleStatus: "FAILED", subtitleLineCount: null,
    subtitleError: "Network error", createTime: "2024-01-03T00:00:00Z",
  },
];

function mockPageResponse(movies: Movie[], totalPages = 1): PageResponse<Movie> {
  return {
    content: movies,
    totalPages,
    totalElements: movies.length,
    number: 0,
    size: 10,
  };
}

describe("MoviesPage", () => {
  let fetchMock: ReturnType<typeof vi.fn>;

  function setupFetchMock(movies: Movie[] = mockMovies, totalPages = 1): void {
    fetchMock = vi.fn((url: string | URL | Request) => {
      const urlStr = String(url);
      if (urlStr.includes("/api/movies/") && urlStr.includes("/download")) {
        return Promise.resolve({ ok: true, json: () => Promise.resolve({ status: "ok" }) });
      }
      if (urlStr.includes("/api/movies/")) {
        return Promise.resolve({ ok: true, json: () => Promise.resolve({ status: "deleted" }) });
      }
      if (urlStr.includes("/api/movies")) {
        return Promise.resolve({ ok: true, json: () => Promise.resolve(mockPageResponse(movies, totalPages)) });
      }
      return Promise.resolve({ ok: true, json: () => Promise.resolve({}) });
    });
    vi.stubGlobal("fetch", fetchMock);
  }

  beforeEach(() => {
    setupFetchMock();
  });

  it("renders toolbar and loads movies on mount", async () => {
    render(<MoviesApp />);
    await waitFor(() => {
      expect(screen.getByTestId("movies-toolbar")).toBeDefined();
    });
    const titles = screen.getAllByTestId("movie-title");
    expect(titles).toHaveLength(3);
    expect(titles[0].textContent).toBe("Inception");
  });

  it("shows empty state when no movies exist", async () => {
    setupFetchMock([]);
    render(<MoviesApp />);
    await waitFor(() => {
      expect(screen.getByTestId("movies-empty")).toBeDefined();
    });
    expect(screen.getByTestId("movies-empty").textContent).toBe("暂无电影");
  });

  it("shows loading state initially", () => {
    render(<MoviesApp />);
    expect(screen.getByTestId("movies-loading")).toBeDefined();
  });

  it("searches movies on input change with debounce", async () => {
    render(<MoviesApp />);
    await waitFor(() => {
      expect(screen.getAllByTestId("movie-title")).toHaveLength(3);
    });

    const searchInput = screen.getByTestId("movies-search-input");
    fireEvent.change(searchInput, { target: { value: "Inception" } });

    await waitFor(() => {
      const calls = fetchMock.mock.calls.filter(
        (c: unknown[]) => String(c[0]).includes("search=Inception")
      );
      expect(calls.length).toBeGreaterThan(0);
    });
  });

  it("sorts movies on dropdown change", async () => {
    render(<MoviesApp />);
    await waitFor(() => {
      expect(screen.getAllByTestId("movie-title")).toHaveLength(3);
    });

    // Open sort dropdown
    fireEvent.click(screen.getByTestId("movies-sort-btn"));
    // Click an option
    const options = screen.getAllByTestId("movies-sort-option");
    const yearDesc = options.find((o) => o.textContent === "年份 ↓");
    expect(yearDesc).toBeDefined();
    fireEvent.click(yearDesc!);

    await waitFor(() => {
      const calls = fetchMock.mock.calls.filter(
        (c: unknown[]) => String(c[0]).includes("sort=releaseYear%2Cdesc")
      );
      expect(calls.length).toBeGreaterThan(0);
    });
  });

  it("paginates when page changes", async () => {
    setupFetchMock(mockMovies.slice(0, 2), 2);
    render(<MoviesApp />);
    await waitFor(() => {
      expect(screen.getAllByTestId("movie-title")).toHaveLength(2);
    });

    const nextBtn = screen.getByTestId("page-next");
    fireEvent.click(nextBtn);

    await waitFor(() => {
      const calls = fetchMock.mock.calls.filter(
        (c: unknown[]) => String(c[0]).includes("page=1")
      );
      expect(calls.length).toBeGreaterThan(0);
    });
  });

  it("renders Header component", () => {
    render(<MoviesApp />);
    expect(screen.getByTestId("nav-menu-btn")).toBeDefined();
  });

  it("shows error message on fetch failure", async () => {
    vi.stubGlobal("fetch", vi.fn(() =>
      Promise.resolve({ ok: false, status: 500 })
    ));
    render(<MoviesApp />);
    await waitFor(() => {
      expect(screen.getByTestId("movies-error")).toBeDefined();
    });
  });
});
