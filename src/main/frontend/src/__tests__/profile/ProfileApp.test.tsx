import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { render, waitFor } from "@testing-library/react";
import { ProfileApp } from "../../components/profile/ProfileApp";

describe("ProfileApp", () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("shows password form for regular user", async () => {
    global.fetch = vi.fn().mockResolvedValue({
      ok: true,
      json: () => Promise.resolve({ username: "alice", admin: false }),
    } as Response);

    const { findByTestId } = render(<ProfileApp />);
    const form = await findByTestId("current-password-input");
    expect(form).toBeInTheDocument();
  });

  it("shows user management for admin user", async () => {
    (global.fetch as ReturnType<typeof vi.fn>).mockImplementation((url: string) => {
      if (url === "/api/user/me") {
        return Promise.resolve({
          ok: true,
          json: () => Promise.resolve({ username: "admin", admin: true }),
        } as Response);
      }
      if (url === "/api/admin/users") {
        return Promise.resolve({
          ok: true,
          json: () => Promise.resolve([]),
        } as Response);
      }
      return Promise.resolve({ ok: true } as Response);
    });

    const { findByTestId } = render(<ProfileApp />);
    const title = await findByTestId("user-management-title");
    expect(title).toBeInTheDocument();
  });

  it("shows loading state initially", () => {
    global.fetch = vi.fn().mockImplementation(() => new Promise(() => {}));

    const { getByTestId } = render(<ProfileApp />);
    expect(getByTestId("profile-loading")).toBeInTheDocument();
  });

  it("shows error on fetch failure", async () => {
    global.fetch = vi.fn().mockRejectedValue(new Error("network"));

    const { findByTestId } = render(<ProfileApp />);
    const error = await findByTestId("profile-error");
    expect(error).toBeInTheDocument();
  });
});
