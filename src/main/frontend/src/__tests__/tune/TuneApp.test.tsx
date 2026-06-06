import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { render, waitFor } from "@testing-library/react";
import { TuneApp } from "../../components/tune/TuneApp";

describe("TuneApp", () => {
  let originalFetch: typeof global.fetch;

  beforeEach(() => {
    originalFetch = global.fetch;
    global.fetch = vi.fn();
  });

  afterEach(() => {
    global.fetch = originalFetch;
  });

  it("fetches user info and displays review count", async () => {
    (global.fetch as ReturnType<typeof vi.fn>)
      .mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve({ username: "testuser", admin: false }),
      })
      .mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve({ username: "testuser", admin: false }),
      })
      .mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve({ count: 850, threshold: 512 }),
      })
      .mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve({ content: [], totalPages: 0, number: 0 }),
      })
      .mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve({ content: [], totalPages: 0, number: 0 }),
      });

    const { findByText } = render(<TuneApp />);
    await findByText("850 / 512");
  });

  it("renders user selector for admin", async () => {
    (global.fetch as ReturnType<typeof vi.fn>)
      .mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve({ username: "admin", admin: true }),
      })
      .mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve({ username: "admin", admin: true }),
      })
      .mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve([{ username: "user1" }]),
      })
      .mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve({ count: 100, threshold: 512 }),
      })
      .mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve({ content: [], totalPages: 0, number: 0 }),
      })
      .mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve({ content: [], totalPages: 0, number: 0 }),
      });

    const { findByText } = render(<TuneApp />);
    const select = (await findByText("admin")).closest("select") as HTMLSelectElement;
    expect(select).not.toBeNull();
    expect(select.options.length).toBe(2);
    expect(select.options[0].value).toBe("admin");
  });

  it("shows Optimize and Reschedule section titles", async () => {
    (global.fetch as ReturnType<typeof vi.fn>)
      .mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve({ username: "testuser", admin: false }),
      })
      .mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve({ username: "testuser", admin: false }),
      })
      .mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve({ count: 0, threshold: 512 }),
      })
      .mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve({ content: [], totalPages: 0, number: 0 }),
      })
      .mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve({ content: [], totalPages: 0, number: 0 }),
      });

    const { findByText } = render(<TuneApp />);
    await findByText("Optimize Logs");
    await findByText("Reschedule Logs");
  });
});
