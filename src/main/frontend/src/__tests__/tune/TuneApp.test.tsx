import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { render } from "@testing-library/react";
import { TuneApp } from "../../components/tune/TuneApp";

function routeFetch(url: string, _init?: RequestInit): Response {
  if (url === "/api/user/me") {
    return { ok: true, json: () => Promise.resolve({ username: "testuser", admin: false }) } as Response;
  }
  if (url === "/api/user/preferences" && (!_init || _init.method === undefined)) {
    return { ok: true, json: () => Promise.resolve({}) } as Response;
  }
  if (url === "/api/user/preferences" && _init?.method === "PUT") {
    return { ok: true, json: () => Promise.resolve({}) } as Response;
  }
  if (url.startsWith("/api/tune/review-count")) {
    return { ok: true, json: () => Promise.resolve({ count: 850, threshold: 512 }) } as Response;
  }
  if (url.startsWith("/api/tune/optimize-logs")) {
    return { ok: true, json: () => Promise.resolve({ content: [], totalPages: 0, number: 0 }) } as Response;
  }
  if (url.startsWith("/api/tune/reschedule-logs")) {
    return { ok: true, json: () => Promise.resolve({ content: [], totalPages: 0, number: 0 }) } as Response;
  }
  return { ok: true, json: () => Promise.resolve({}) } as Response;
}

describe("TuneApp", () => {
  let originalFetch: typeof global.fetch;

  beforeEach(() => {
    originalFetch = global.fetch;
    global.fetch = vi.fn((url: string, init?: RequestInit) =>
      Promise.resolve(routeFetch(url, init))
    ) as typeof global.fetch;
  });

  afterEach(() => {
    global.fetch = originalFetch;
  });

  it("fetches user info and displays review count", async () => {
    const { findByText } = render(<TuneApp />);
    await findByText("850 / 512");
  });

  it("renders user selector for admin", async () => {
    global.fetch = vi.fn((url: string, init?: RequestInit) => {
      if (url === "/api/user/me") {
        return Promise.resolve({ ok: true, json: () => Promise.resolve({ username: "admin", admin: true }) } as Response);
      }
      if (url === "/api/admin/users") {
        return Promise.resolve({ ok: true, json: () => Promise.resolve([{ username: "user1" }]) } as Response);
      }
      return Promise.resolve(routeFetch(url, init));
    }) as typeof global.fetch;

    const { findByText } = render(<TuneApp />);
    const select = (await findByText("admin")).closest("select") as HTMLSelectElement;
    expect(select).not.toBeNull();
    expect(select.options.length).toBe(2);
    expect(select.options[0].value).toBe("admin");
  });

  it("shows Optimize and Reschedule section titles", async () => {
    const { findByText } = render(<TuneApp />);
    await findByText("Optimize Logs");
    await findByText("Reschedule Logs");
  });
});
