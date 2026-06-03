import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, fireEvent, screen, waitFor } from "@testing-library/react";
import { ManageApp } from "../../components/manage/ManageApp";

function setupFetchMocks() {
  (global as any).fetch = vi.fn((url: string) => {
    const urlStr = String(url);
    if (urlStr.includes("/api/cards")) {
      return Promise.resolve({
        ok: true,
        json: () => Promise.resolve({ content: [], totalPages: 0, totalElements: 0, number: 0, size: 10 }),
      });
    }
    if (urlStr.includes("/api/tags")) {
      return Promise.resolve({ ok: true, json: () => Promise.resolve([]) });
    }
    return Promise.resolve({ ok: true, json: () => Promise.resolve([]) });
  });
}

describe("ManageApp", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    setupFetchMocks();
  });

  it("renders Header with nav menu button", async () => {
    render(<ManageApp />);
    await waitFor(() => {
      expect(screen.getByTestId("nav-menu-btn")).toBeInTheDocument();
    });
  });

  it("renders Cards tab by default", async () => {
    render(<ManageApp />);
    await waitFor(() => {
      expect(screen.getByTestId("tab-cards")).toBeInTheDocument();
      expect(screen.getByTestId("tab-cards").getAttribute("data-active")).toBe("true");
    });
  });

  it("switches to Tags tab when clicked", async () => {
    render(<ManageApp />);
    await waitFor(() => {
      expect(screen.getByTestId("tab-tags")).toBeInTheDocument();
    });
    fireEvent.click(screen.getByTestId("tab-tags"));

    await waitFor(() => {
      expect(screen.getByTestId("tab-tags").getAttribute("data-active")).toBe("true");
      expect(screen.getByTestId("tab-cards").getAttribute("data-active")).toBe("false");
    });
  });

  it("renders manage-layout structure", async () => {
    const { container } = render(<ManageApp />);
    await waitFor(() => {
      expect(container.querySelector(".manage-layout")).toBeInTheDocument();
    });
  });
});
