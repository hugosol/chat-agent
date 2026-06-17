import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, fireEvent, screen, waitFor } from "@testing-library/react";
import { MovieRetryModal } from "../../components/movies/MovieRetryModal";

describe("MovieRetryModal", () => {
  let fetchMock: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);
  });

  it("renders retry text and confirm button when open", () => {
    render(
      <MovieRetryModal
        open={true}
        movie={{ title: "Inception", imdbId: "tt001" }}
        onClose={vi.fn()}
        onRetried={vi.fn()}
      />
    );
    const text = screen.getByTestId("movie-retry-text");
    expect(text.textContent).toContain("Inception");
    expect(text.textContent).toContain("重新下载");
    expect(screen.getByTestId("modal-save")).toBeDefined();
  });

  it("calls download API and shows loading spinner when confirm is clicked", async () => {
    // Never-resolving promise keeps retrying=true for assertion
    const fetchPromise = new Promise<Response>(() => {});
    fetchMock.mockReturnValueOnce(fetchPromise);

    render(
      <MovieRetryModal
        open={true}
        movie={{ title: "Inception", imdbId: "tt001" }}
        onClose={vi.fn()}
        onRetried={vi.fn()}
      />
    );

    fireEvent.click(screen.getByTestId("modal-save"));

    await waitFor(() => {
      expect(screen.getByTestId("movie-retry-loading")).toBeDefined();
    });

    const calls = fetchMock.mock.calls.filter(
      (c: unknown[]) => String(c[0]).includes("/api/movies/tt001/download")
    );
    expect(calls.length).toBeGreaterThan(0);
  });

  it("disables confirm button during loading", async () => {
    const fetchPromise = new Promise<Response>(() => {});
    fetchMock.mockReturnValueOnce(fetchPromise);

    render(
      <MovieRetryModal
        open={true}
        movie={{ title: "Inception", imdbId: "tt001" }}
        onClose={vi.fn()}
        onRetried={vi.fn()}
      />
    );

    fireEvent.click(screen.getByTestId("modal-save"));

    await waitFor(() => {
      const btn = screen.getByTestId("modal-save") as HTMLButtonElement;
      expect(btn.disabled).toBe(true);
    });
  });

  it("closes modal and calls onRetried on success", async () => {
    fetchMock.mockResolvedValueOnce({ ok: true });
    const onRetried = vi.fn();

    render(
      <MovieRetryModal
        open={true}
        movie={{ title: "Inception", imdbId: "tt001" }}
        onClose={vi.fn()}
        onRetried={onRetried}
      />
    );

    fireEvent.click(screen.getByTestId("modal-save"));

    await waitFor(() => {
      expect(onRetried).toHaveBeenCalled();
    });
  });

  it("shows error message on failure", async () => {
    fetchMock.mockResolvedValueOnce({ ok: false, status: 500 });
    const onRetried = vi.fn();

    render(
      <MovieRetryModal
        open={true}
        movie={{ title: "Inception", imdbId: "tt001" }}
        onClose={vi.fn()}
        onRetried={onRetried}
      />
    );

    fireEvent.click(screen.getByTestId("modal-save"));

    await waitFor(() => {
      expect(screen.getByTestId("movie-retry-error")).toBeDefined();
    });
    expect(onRetried).not.toHaveBeenCalled();
  });
});
