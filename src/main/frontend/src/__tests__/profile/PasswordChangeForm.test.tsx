import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { render, fireEvent, waitFor } from "@testing-library/react";
import { PasswordChangeForm } from "../../components/profile/PasswordChangeForm";

describe("PasswordChangeForm", () => {
  beforeEach(() => {
    global.fetch = vi.fn();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("renders three password input fields", () => {
    const { getByTestId } = render(<PasswordChangeForm />);
    expect(getByTestId("current-password-input")).toBeInTheDocument();
    expect(getByTestId("new-password-input")).toBeInTheDocument();
    expect(getByTestId("confirm-password-input")).toBeInTheDocument();
  });

  it("renders a submit button", () => {
    const { getByTestId } = render(<PasswordChangeForm />);
    expect(getByTestId("change-password-btn")).toBeInTheDocument();
  });

  it("shows error when new password is less than 6 characters", async () => {
    const { getByTestId, findByTestId } = render(<PasswordChangeForm />);
    fireEvent.change(getByTestId("current-password-input"), { target: { value: "oldpass" } });
    fireEvent.change(getByTestId("new-password-input"), { target: { value: "abc" } });
    fireEvent.change(getByTestId("confirm-password-input"), { target: { value: "abc" } });
    fireEvent.click(getByTestId("change-password-btn"));
    const error = await findByTestId("password-error");
    expect(error.textContent).toContain("6");
  });

  it("shows error when confirm password does not match", async () => {
    const { getByTestId, findByTestId } = render(<PasswordChangeForm />);
    fireEvent.change(getByTestId("current-password-input"), { target: { value: "oldpass" } });
    fireEvent.change(getByTestId("new-password-input"), { target: { value: "newpass123" } });
    fireEvent.change(getByTestId("confirm-password-input"), { target: { value: "different" } });
    fireEvent.click(getByTestId("change-password-btn"));
    const error = await findByTestId("password-error");
    expect(error.textContent).toContain("match");
  });

  it("calls API with correct payload on valid submit", async () => {
    const mockFetch = vi.fn().mockResolvedValue({ ok: true } as Response);
    global.fetch = mockFetch;

    const { getByTestId } = render(<PasswordChangeForm />);
    fireEvent.change(getByTestId("current-password-input"), { target: { value: "oldpass" } });
    fireEvent.change(getByTestId("new-password-input"), { target: { value: "newpass123" } });
    fireEvent.change(getByTestId("confirm-password-input"), { target: { value: "newpass123" } });
    fireEvent.click(getByTestId("change-password-btn"));

    await waitFor(() => {
      expect(mockFetch).toHaveBeenCalledWith("/api/user/password", {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ currentPassword: "oldpass", newPassword: "newpass123" }),
      });
    });
  });

  it("shows success message on successful password change", async () => {
    (global.fetch as ReturnType<typeof vi.fn>).mockResolvedValue({ ok: true } as Response);

    const { getByTestId, findByTestId } = render(<PasswordChangeForm />);
    fireEvent.change(getByTestId("current-password-input"), { target: { value: "oldpass" } });
    fireEvent.change(getByTestId("new-password-input"), { target: { value: "newpass123" } });
    fireEvent.change(getByTestId("confirm-password-input"), { target: { value: "newpass123" } });
    fireEvent.click(getByTestId("change-password-btn"));

    const success = await findByTestId("password-success");
    expect(success).toBeInTheDocument();
  });

  it("shows error message on API failure", async () => {
    (global.fetch as ReturnType<typeof vi.fn>).mockResolvedValue({ ok: false } as Response);

    const { getByTestId, findByTestId } = render(<PasswordChangeForm />);
    fireEvent.change(getByTestId("current-password-input"), { target: { value: "oldpass" } });
    fireEvent.change(getByTestId("new-password-input"), { target: { value: "newpass123" } });
    fireEvent.change(getByTestId("confirm-password-input"), { target: { value: "newpass123" } });
    fireEvent.click(getByTestId("change-password-btn"));

    const error = await findByTestId("password-error");
    expect(error).toBeInTheDocument();
  });
});
