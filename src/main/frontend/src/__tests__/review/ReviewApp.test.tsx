import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, waitFor } from "@testing-library/react";
import { ReviewApp } from "../../components/review/ReviewApp";

describe("ReviewApp", () => {
  beforeEach(() => {
    (global as unknown as { fetch: typeof fetch }).fetch = vi.fn((url: string) => {
      if (url === "/api/review/decks") {
        return Promise.resolve({
          ok: true,
          json: () => Promise.resolve([]),
        } as Response);
      }
      if (url === "/api/user/preferences") {
        return Promise.resolve({
          ok: true,
          json: () => Promise.resolve({}),
        } as Response);
      }
      return Promise.resolve({ ok: true, json: () => Promise.resolve({}) } as Response);
    });
  });

  it("renders with a fixed layout container", async () => {
    const { container } = render(<ReviewApp />);

    await waitFor(() => {
      const appDiv = container.querySelector(".app");
      expect(appDiv).toBeTruthy();
    });
  });
});
