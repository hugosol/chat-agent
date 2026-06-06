import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { render, fireEvent, waitFor } from "@testing-library/react";
import { UserManagement } from "../../components/profile/UserManagement";

describe("UserManagement", () => {
  const mockUsers = [
    { id: "u1", username: "alice", createTime: "2026-01-01", enabled: true },
    { id: "u2", username: "bob", createTime: "2026-01-02", enabled: false },
  ];

  beforeEach(() => {
    global.fetch = vi.fn((url: string) => {
      if (url === "/api/admin/users") {
        return Promise.resolve({
          ok: true,
          json: () => Promise.resolve(mockUsers),
        } as Response);
      }
      return Promise.resolve({ ok: true } as Response);
    });
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("fetches and displays user list", async () => {
    const { findByText } = render(<UserManagement />);
    const alice = await findByText("alice");
    const bob = await findByText("bob");
    expect(alice).toBeInTheDocument();
    expect(bob).toBeInTheDocument();
  });

  it("shows enabled status for each user", async () => {
    const { findByTestId } = render(<UserManagement />);
    const aliceStatus = await findByTestId("user-status-u1");
    expect(aliceStatus.textContent).toContain("Enabled");
    const bobStatus = await findByTestId("user-status-u2");
    expect(bobStatus.textContent).toContain("Disabled");
  });

  it("shows create user button", async () => {
    const { findByTestId } = render(<UserManagement />);
    const btn = await findByTestId("create-user-btn");
    expect(btn).toBeInTheDocument();
  });

  it("shows create user modal when button clicked", async () => {
    const { findByTestId } = render(<UserManagement />);
    const btn = await findByTestId("create-user-btn");
    fireEvent.click(btn);
    const modal = await findByTestId("create-user-modal");
    expect(modal).toBeInTheDocument();
  });

  it("shows disable confirmation modal when disable clicked", async () => {
    const { findByTestId } = render(<UserManagement />);
    const disableBtn = await findByTestId("disable-btn-u1");
    fireEvent.click(disableBtn);
    const modal = await findByTestId("confirm-disable-modal");
    expect(modal).toBeInTheDocument();
  });

  it("shows reset password modal when reset clicked", async () => {
    const { findByTestId } = render(<UserManagement />);
    const resetBtn = await findByTestId("reset-password-btn-u1");
    fireEvent.click(resetBtn);
    const modal = await findByTestId("reset-password-modal");
    expect(modal).toBeInTheDocument();
  });
});
