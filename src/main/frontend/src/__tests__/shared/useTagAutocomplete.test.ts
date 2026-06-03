import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { renderHook, act, waitFor } from "@testing-library/react";
import { useTagAutocomplete } from "../../shared/useTagAutocomplete";

interface Tag {
  id: string;
  name: string;
  type: string | null;
}

describe("useTagAutocomplete", () => {
  beforeEach(() => {
    const mockTags: Tag[] = [
      { id: "1", name: "work", type: "deck" },
      { id: "2", name: "vocab", type: null },
    ];
    (globalThis as Record<string, unknown>).fetch = vi.fn().mockResolvedValue({
      ok: true,
      json: () => Promise.resolve(mockTags),
    });
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("fetches tags on mount by default and exposes them as options", async () => {
    const { result } = renderHook(() => useTagAutocomplete("/api/tags"));

    expect(globalThis.fetch).toHaveBeenCalledWith("/api/tags", {
      credentials: "same-origin",
    });

    await waitFor(() => {
      expect(result.current.options).toHaveLength(2);
    });
    expect(result.current.options[0].name).toBe("work");
    expect(result.current.selected).toEqual([]);
  });

  it("does not fetch on mount when defer is true", () => {
    renderHook(() => useTagAutocomplete("/api/tags", { defer: true }));
    expect(globalThis.fetch).not.toHaveBeenCalled();
  });

  it("fetches when fetchTags is called in defer mode", async () => {
    const { result } = renderHook(() =>
      useTagAutocomplete("/api/tags", { defer: true })
    );

    act(() => {
      result.current.fetchTags();
    });

    expect(globalThis.fetch).toHaveBeenCalledWith("/api/tags", {
      credentials: "same-origin",
    });
  });

  it("setSelected updates the selected chips", () => {
    const { result } = renderHook(() => useTagAutocomplete("/api/tags"));
    const tag: Tag = { id: "1", name: "work", type: "deck" };
    act(() => {
      result.current.setSelected([tag]);
    });
    expect(result.current.selected).toEqual([tag]);
  });
});
