import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, fireEvent, screen, waitFor } from "@testing-library/react";
import { TagsTab } from "../../components/manage/TagsTab";
import type { Tag } from "../../shared/types";

const mockTags: Tag[] = [
  { id: 1, name: "Daily English", type: "deck" },
  { id: 2, name: "verb", type: null },
];

function setupFetchMocks() {
  (global as any).fetch = vi.fn((url: string) => {
    const urlStr = String(url);
    if (urlStr === "/api/tags") {
      return Promise.resolve({ ok: true, json: () => Promise.resolve(mockTags) });
    }
    return Promise.resolve({ ok: true, json: () => Promise.resolve([]) });
  });
}

describe("TagsTab", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    setupFetchMocks();
  });

  it("loads and displays tags", async () => {
    render(<TagsTab />);
    await waitFor(() => {
      expect(screen.getByText("Daily English")).toBeInTheDocument();
    });
    expect(screen.getByText("verb")).toBeInTheDocument();
  });

  it("opens create tag modal when create button clicked", async () => {
    render(<TagsTab />);
    await waitFor(() => {
      expect(screen.getByText("创建标签")).toBeInTheDocument();
    });
    fireEvent.click(screen.getByText("创建标签"));
    expect(screen.getByTestId("modal-overlay")).toBeInTheDocument();
  });

  it("starts inline edit when Edit clicked", async () => {
    render(<TagsTab />);
    await waitFor(() => {
      expect(screen.getAllByTestId("btn-edit-tag")).toHaveLength(2);
    });
    fireEvent.click(screen.getAllByTestId("btn-edit-tag")[0]);
    expect(screen.getByTestId("edit-name-input")).toBeInTheDocument();
  });

  it("opens delete confirm when Delete clicked", async () => {
    render(<TagsTab />);
    await waitFor(() => {
      expect(screen.getAllByTestId("btn-delete-tag")).toHaveLength(2);
    });
    fireEvent.click(screen.getAllByTestId("btn-delete-tag")[0]);
    await waitFor(() => {
      expect(screen.getByTestId("modal-save")).toHaveTextContent("Delete");
    });
  });

  it("sends DELETE request when delete confirmed", async () => {
    render(<TagsTab />);
    await waitFor(() => {
      expect(screen.getAllByTestId("btn-delete-tag")).toHaveLength(2);
    });
    fireEvent.click(screen.getAllByTestId("btn-delete-tag")[1]);
    await waitFor(() => {
      expect(screen.getByTestId("modal-overlay")).toBeInTheDocument();
    });
    fireEvent.click(screen.getByTestId("modal-save"));

    await waitFor(() => {
      expect(global.fetch).toHaveBeenCalledWith(
        expect.stringContaining("/api/tags/2"),
        expect.objectContaining({ method: "DELETE" })
      );
    });
  });
});
