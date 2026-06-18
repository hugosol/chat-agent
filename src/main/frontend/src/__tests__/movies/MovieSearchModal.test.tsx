import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, fireEvent, screen, waitFor } from "@testing-library/react";
import { MovieSearchModal } from "../../components/movies/MovieSearchModal";

describe("MovieSearchModal", () => {
  let fetchMock: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);
  });

  it("renders nothing when closed", () => {
    const { container } = render(
      <MovieSearchModal open={false} onClose={vi.fn()} onAdded={vi.fn()} />
    );
    expect(container.innerHTML).toBe("");
  });

  it("renders search input when open", () => {
    render(<MovieSearchModal open={true} onClose={vi.fn()} onAdded={vi.fn()} />);
    expect(screen.getByTestId("movie-search-query")).toBeDefined();
  });

  it("searches TMDB on input with debounce", async () => {
    fetchMock.mockResolvedValue({
      ok: true,
      json: () => Promise.resolve([{ imdbId: "tt001", title: "Test", year: 2022 }]),
    });

    render(<MovieSearchModal open={true} onClose={vi.fn()} onAdded={vi.fn()} />);
    const input = screen.getByTestId("movie-search-query");
    fireEvent.change(input, { target: { value: "Test Movie" } });

    await waitFor(() => {
      expect(fetchMock).toHaveBeenCalled();
    }, { timeout: 500 });
  });

  it("displays candidate results", async () => {
    fetchMock.mockResolvedValue({
      ok: true,
      json: () => Promise.resolve([
        { imdbId: "tt001", title: "Inception", year: 2010 },
      ]),
    });

    render(<MovieSearchModal open={true} onClose={vi.fn()} onAdded={vi.fn()} />);
    const input = screen.getByTestId("movie-search-query");
    fireEvent.change(input, { target: { value: "Inception" } });

    await waitFor(() => {
      expect(screen.getByTestId("movie-search-add-btn")).toBeDefined();
    }, { timeout: 500 });
  });

  it("shows no results message when empty", async () => {
    fetchMock.mockResolvedValue({
      ok: true,
      json: () => Promise.resolve([]),
    });

    render(<MovieSearchModal open={true} onClose={vi.fn()} onAdded={vi.fn()} />);
    const input = screen.getByTestId("movie-search-query");
    fireEvent.change(input, { target: { value: "NoSuchMovie" } });

    await waitFor(() => {
      expect(screen.getByTestId("movie-search-empty")).toBeDefined();
    }, { timeout: 500 });
  });

  it("adds movie and calls onAdded", async () => {
    fetchMock.mockImplementation((url: string | URL | Request, init?: RequestInit) => {
      const urlStr = String(url);
      if (urlStr.includes("/api/movies/search")) {
        return Promise.resolve({
          ok: true,
          json: () => Promise.resolve([{ imdbId: "tt001", title: "Test", year: 2022 }]),
        });
      }
      // POST /api/movies (add)
      return Promise.resolve({ ok: true, json: () => Promise.resolve({}) });
    });

    const onAdded = vi.fn();
    render(<MovieSearchModal open={true} onClose={vi.fn()} onAdded={onAdded} />);
    const input = screen.getByTestId("movie-search-query");
    fireEvent.change(input, { target: { value: "Test" } });

    await waitFor(() => {
      expect(screen.getByTestId("movie-search-add-btn")).toBeDefined();
    }, { timeout: 500 });

    fireEvent.click(screen.getByTestId("movie-search-add-btn"));

    await waitFor(() => {
      expect(onAdded).toHaveBeenCalled();
    });
  });
});
