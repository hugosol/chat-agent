import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, fireEvent, screen, waitFor } from "@testing-library/react";
import { MovieImportModal } from "../../components/movies/MovieImportModal";

function createMockFile(content: string, name = "test.csv"): File {
  return new File([content], name, { type: "text/csv" });
}

describe("MovieImportModal", () => {
  let fetchMock: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);
  });

  it("renders nothing when closed", () => {
    const { container } = render(
      <MovieImportModal open={false} onClose={vi.fn()} onImported={vi.fn()} />
    );
    expect(container.innerHTML).toBe("");
  });

  it("shows format hint and file input when open", () => {
    render(<MovieImportModal open={true} onClose={vi.fn()} onImported={vi.fn()} />);
    expect(screen.getByTestId("movie-import-file")).toBeDefined();
    expect(screen.getByTestId("movie-import-modal").textContent).toContain("imdbId,title,year");
  });

  it("parses CSV and shows upload button with valid row count", async () => {
    const csvContent = "imdbId,title,year\n" +
      "tt1375666,Inception,2010\n" +
      "tt0133093,The Matrix,1999";

    render(<MovieImportModal open={true} onClose={vi.fn()} onImported={vi.fn()} />);

    const fileInput = screen.getByTestId("movie-import-file");
    const file = createMockFile(csvContent);
    fireEvent.change(fileInput, { target: { files: [file] } });

    await waitFor(() => {
      expect(screen.getByTestId("movie-import-upload-btn")).toBeDefined();
    });
  });

  it("skips header row on CSV parse", async () => {
    const csvContent = "imdbId,title,year\n" +
      "tt1375666,Inception,2010";

    render(<MovieImportModal open={true} onClose={vi.fn()} onImported={vi.fn()} />);

    const fileInput = screen.getByTestId("movie-import-file");
    fireEvent.change(fileInput, { target: { files: [createMockFile(csvContent)] } });

    await waitFor(() => {
      const btn = screen.getByTestId("movie-import-upload-btn");
      expect(btn.textContent).toContain("1");
      expect(btn.textContent).not.toContain("2");
    });
  });

  it("detects header by first column and skips it", async () => {
    // imdbId column header is non-numeric, so it's treated as header
    const csvContent = "imdbId,title,year\n" +
      "tt001,Movie,2020";

    render(<MovieImportModal open={true} onClose={vi.fn()} onImported={vi.fn()} />);

    const fileInput = screen.getByTestId("movie-import-file");
    fireEvent.change(fileInput, { target: { files: [createMockFile(csvContent)] } });

    await waitFor(() => {
      const btn = screen.getByTestId("movie-import-upload-btn");
      expect(btn.textContent).toContain("1 部电影");
    });
  });

  it("reports invalid imdbId format", async () => {
    const csvContent = "tt001,Movie,2020\n" +
      "badid,Bad Movie,2020";

    render(<MovieImportModal open={true} onClose={vi.fn()} onImported={vi.fn()} />);

    const fileInput = screen.getByTestId("movie-import-file");
    fireEvent.change(fileInput, { target: { files: [createMockFile(csvContent)] } });

    await waitFor(() => {
      expect(screen.getByTestId("movie-import-modal").textContent).toContain("imdbId 格式无效");
    });
  });

  it("uploads and shows result on success", async () => {
    fetchMock.mockResolvedValueOnce({
      ok: true,
      json: () => Promise.resolve({ imported: 2, status: "ok" }),
    });

    const csvContent = "tt001,Movie A,2020\n" +
      "tt002,Movie B,2021";
    const onImported = vi.fn();

    render(<MovieImportModal open={true} onClose={vi.fn()} onImported={onImported} />);

    const fileInput = screen.getByTestId("movie-import-file");
    fireEvent.change(fileInput, { target: { files: [createMockFile(csvContent)] } });

    await waitFor(() => {
      expect(screen.getByTestId("movie-import-upload-btn")).toBeDefined();
    });

    fireEvent.click(screen.getByTestId("movie-import-upload-btn"));

    await waitFor(() => {
      expect(screen.getByTestId("movie-import-result")).toBeDefined();
    });

    // Click "完成" to trigger onImported
    const doneBtn = screen.getByText("完成");
    fireEvent.click(doneBtn);
    expect(onImported).toHaveBeenCalled();
  });
});
