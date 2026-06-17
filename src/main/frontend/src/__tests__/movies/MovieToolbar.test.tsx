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

  it("renders sort dropdown with all options", () => {
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
    const select = screen.getByTestId("movies-sort-select") as HTMLSelectElement;
    expect(select.value).toBe("title,asc");
    const options = Array.from(select.options).map((o) => o.text);
    expect(options).toContain("名称 A→Z");
    expect(options).toContain("年份 ↓");
    expect(options).toContain("添加时间 ↑");
  });

  it("calls onSortChange when sort is changed", () => {
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
    const select = screen.getByTestId("movies-sort-select");
    fireEvent.change(select, { target: { value: "releaseYear,desc" } });
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
