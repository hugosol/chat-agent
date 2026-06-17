import { describe, it, expect, vi } from "vitest";
import { render, fireEvent, screen, waitFor } from "@testing-library/react";
import { MovieToolbar } from "../../components/movies/MovieToolbar";

describe("MovieToolbar", () => {
  it("renders search input with placeholder", () => {
    render(
      <MovieToolbar
        search=""
        sort="title,asc"
        onSearchChange={vi.fn()}
        onSortChange={vi.fn()}
        onAddMovie={vi.fn()}
        onImportMovies={vi.fn()}
      />
    );
    const input = screen.getByTestId("movies-search-input") as HTMLInputElement;
    expect(input.placeholder).toBe("搜索电影标题...");
    expect(input.value).toBe("");
  });

  it("renders search input with external value", () => {
    render(
      <MovieToolbar
        search="Inception"
        sort="title,asc"
        onSearchChange={vi.fn()}
        onSortChange={vi.fn()}
        onAddMovie={vi.fn()}
        onImportMovies={vi.fn()}
      />
    );
    const input = screen.getByTestId("movies-search-input") as HTMLInputElement;
    expect(input.value).toBe("Inception");
  });

  it("calls onSearchChange with debounce on input", async () => {
    const onSearchChange = vi.fn();
    render(
      <MovieToolbar
        search=""
        sort="title,asc"
        onSearchChange={onSearchChange}
        onSortChange={vi.fn()}
        onAddMovie={vi.fn()}
        onImportMovies={vi.fn()}
      />
    );
    const input = screen.getByTestId("movies-search-input");
    fireEvent.change(input, { target: { value: "Matrix" } });

    await waitFor(() => {
      expect(onSearchChange).toHaveBeenCalledWith("Matrix");
    }, { timeout: 500 });
  });

  it("renders sort dropdown with current selection as label", () => {
    render(
      <MovieToolbar
        search=""
        sort="title,asc"
        onSearchChange={vi.fn()}
        onSortChange={vi.fn()}
        onAddMovie={vi.fn()}
        onImportMovies={vi.fn()}
      />
    );
    // Sort trigger button shows the current selection label
    const trigger = screen.getByTestId("movies-sort-btn");
    expect(trigger.textContent).toBe("名称 A→Z");
  });

  it("calls onSortChange when sort option is clicked", () => {
    const onSortChange = vi.fn();
    render(
      <MovieToolbar
        search=""
        sort="title,asc"
        onSearchChange={vi.fn()}
        onSortChange={onSortChange}
        onAddMovie={vi.fn()}
        onImportMovies={vi.fn()}
      />
    );
    // Open the dropdown
    fireEvent.click(screen.getByTestId("movies-sort-btn"));
    // Click an option
    const options = screen.getAllByTestId("movies-sort-option");
    const yearDesc = options.find((o) => o.textContent === "年份 ↓");
    expect(yearDesc).toBeDefined();
    fireEvent.click(yearDesc!);
    expect(onSortChange).toHaveBeenCalledWith("releaseYear,desc");
  });

  it("calls onAddMovie when add button is clicked", () => {
    const onAddMovie = vi.fn();
    render(
      <MovieToolbar
        search=""
        sort="title,asc"
        onSearchChange={vi.fn()}
        onSortChange={vi.fn()}
        onAddMovie={onAddMovie}
        onImportMovies={vi.fn()}
      />
    );
    fireEvent.click(screen.getByTestId("movies-add-btn"));
    expect(onAddMovie).toHaveBeenCalledTimes(1);
  });

  it("calls onImportMovies when import button is clicked", () => {
    const onImportMovies = vi.fn();
    render(
      <MovieToolbar
        search=""
        sort="title,asc"
        onSearchChange={vi.fn()}
        onSortChange={vi.fn()}
        onAddMovie={vi.fn()}
        onImportMovies={onImportMovies}
      />
    );
    fireEvent.click(screen.getByTestId("movies-import-btn"));
    expect(onImportMovies).toHaveBeenCalledTimes(1);
  });
});
