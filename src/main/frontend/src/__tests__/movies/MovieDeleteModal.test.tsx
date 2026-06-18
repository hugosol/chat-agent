import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, fireEvent, screen, waitFor } from "@testing-library/react";
import { MovieDeleteModal } from "../../components/movies/MovieDeleteModal";
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

describe("MovieDeleteModal", () => {
  let fetchMock: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);
  });

  it("shows subtitle line count in delete confirmation", () => {
    render(
      <MovieDeleteModal
        open={true}
        movie={makeMovie({ subtitleLineCount: 3421 })}
        onClose={vi.fn()}
        onDeleted={vi.fn()}
      />
    );
    const text = screen.getByTestId("movie-delete-text");
    expect(text.textContent).toContain("3,421 行");
    expect(text.textContent).toContain("Inception");
    expect(text.textContent).toContain("(2010)");
  });

  it("shows no-subtitle message when line count is zero", () => {
    render(
      <MovieDeleteModal
        open={true}
        movie={makeMovie({ subtitleLineCount: 0 })}
        onClose={vi.fn()}
        onDeleted={vi.fn()}
      />
    );
    const text = screen.getByTestId("movie-delete-text");
    expect(text.textContent).toContain("暂无字幕数据");
    expect(text.textContent).not.toContain("行");
  });

  it("calls DELETE API and onDeleted on confirm", async () => {
    fetchMock.mockResolvedValueOnce({ ok: true, json: () => Promise.resolve({}) });
    const onDeleted = vi.fn();

    render(
      <MovieDeleteModal
        open={true}
        movie={makeMovie()}
        onClose={vi.fn()}
        onDeleted={onDeleted}
      />
    );

    const saveBtn = screen.getByTestId("modal-save");
    fireEvent.click(saveBtn);

    await waitFor(() => {
      const calls = fetchMock.mock.calls.filter(
        (c: unknown[]) => String(c[0]).includes("/api/movies/tt001")
      );
      expect(calls.length).toBeGreaterThan(0);
      expect(onDeleted).toHaveBeenCalled();
    });
  });

  it("shows error on API failure", async () => {
    fetchMock.mockResolvedValueOnce({ ok: false, status: 500 });
    const onDeleted = vi.fn();

    render(
      <MovieDeleteModal
        open={true}
        movie={makeMovie()}
        onClose={vi.fn()}
        onDeleted={onDeleted}
      />
    );

    const saveBtn = screen.getByTestId("modal-save");
    fireEvent.click(saveBtn);

    await waitFor(() => {
      expect(screen.getByTestId("movie-delete-error")).toBeDefined();
    });
    expect(onDeleted).not.toHaveBeenCalled();
  });
});
