import { describe, it, expect, vi } from "vitest";
import { render, fireEvent, screen } from "@testing-library/react";
import { MovieBlock } from "../../components/movies/MovieBlock";
import type { Movie } from "../../shared/types";

function makeMovie(overrides: Partial<Movie> = {}): Movie {
  return {
    id: "1",
    imdbId: "tt001",
    title: "Inception",
    year: 2010,
    subtitleStatus: "DONE",
    subtitleLineCount: 3421,
    subtitleError: null,
    createTime: "2024-01-01T00:00:00Z",
    ...overrides,
  };
}

describe("MovieBlock", () => {
  it("renders title and year", () => {
    render(
      <MovieBlock movie={makeMovie()} onDelete={vi.fn()} onRetry={vi.fn()} />
    );
    expect(screen.getByTestId("movie-title").textContent).toBe("Inception");
    expect(screen.getByTestId("movie-year").textContent).toBe("(2010)");
  });

  it("renders without year when null", () => {
    const movie = makeMovie({ year: null });
    render(<MovieBlock movie={movie} onDelete={vi.fn()} onRetry={vi.fn()} />);
    expect(screen.queryByTestId("movie-year")).toBeNull();
  });

  it("shows DONE status with line count", () => {
    render(
      <MovieBlock movie={makeMovie({ subtitleStatus: "DONE", subtitleLineCount: 3421 })} onDelete={vi.fn()} onRetry={vi.fn()} />
    );
    const status = screen.getByTestId("movie-status");
    expect(status.textContent).toContain("3,421 行");
    expect(status.className).toContain("statusDone");
  });

  it("shows PENDING status", () => {
    render(
      <MovieBlock movie={makeMovie({ subtitleStatus: "PENDING", subtitleLineCount: 0 })} onDelete={vi.fn()} onRetry={vi.fn()} />
    );
    const status = screen.getByTestId("movie-status");
    expect(status.textContent).toContain("等待下载");
  });

  it("shows DOWNLOADING status", () => {
    render(
      <MovieBlock movie={makeMovie({ subtitleStatus: "DOWNLOADING" })} onDelete={vi.fn()} onRetry={vi.fn()} />
    );
    const status = screen.getByTestId("movie-status");
    expect(status.textContent).toContain("下载中");
  });

  it("shows FAILED status with error message and download button", () => {
    render(
      <MovieBlock
        movie={makeMovie({ subtitleStatus: "FAILED", subtitleError: "Network error" })}
        onDelete={vi.fn()}
        onRetry={vi.fn()}
      />
    );
    const status = screen.getByTestId("movie-status");
    expect(status.textContent).toContain("Network error");
    expect(status.className).toContain("statusFailed");
    expect(screen.getByTestId("movie-download-btn")).toBeDefined();
  });

  it("does not show download button when status is DONE", () => {
    render(
      <MovieBlock movie={makeMovie({ subtitleStatus: "DONE" })} onDelete={vi.fn()} onRetry={vi.fn()} />
    );
    expect(screen.queryByTestId("movie-download-btn")).toBeNull();
  });

  it("shows download button when status is PENDING", () => {
    render(
      <MovieBlock movie={makeMovie({ subtitleStatus: "PENDING" })} onDelete={vi.fn()} onRetry={vi.fn()} />
    );
    expect(screen.getByTestId("movie-download-btn")).toBeDefined();
  });

  it("calls onDelete when delete button is clicked", () => {
    const onDelete = vi.fn();
    render(
      <MovieBlock movie={makeMovie()} onDelete={onDelete} onRetry={vi.fn()} />
    );
    fireEvent.click(screen.getByTestId("movie-delete-btn"));
    expect(onDelete).toHaveBeenCalledTimes(1);
    expect(onDelete).toHaveBeenCalledWith(makeMovie());
  });

  it("calls onRetry when download button is clicked", () => {
    const onRetry = vi.fn();
    render(
      <MovieBlock
        movie={makeMovie({ subtitleStatus: "FAILED" })}
        onDelete={vi.fn()}
        onRetry={onRetry}
      />
    );
    fireEvent.click(screen.getByTestId("movie-download-btn"));
    expect(onRetry).toHaveBeenCalledTimes(1);
  });
});
