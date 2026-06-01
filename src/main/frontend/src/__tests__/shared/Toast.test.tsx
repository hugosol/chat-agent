import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { render, act } from "@testing-library/react";
import { Toast, showToast } from "../../shared/Toast";

describe("Toast", () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("renders message and calls onClose after duration", () => {
    const onClose = vi.fn();
    render(<Toast message="Saved!" duration={2000} onClose={onClose} />);

    expect(document.body.textContent).toContain("Saved!");
    expect(onClose).not.toHaveBeenCalled();

    act(() => {
      vi.advanceTimersByTime(2000);
    });
    expect(onClose).toHaveBeenCalledOnce();
  });

  it("clears timer on unmount", () => {
    const onClose = vi.fn();
    const { unmount } = render(<Toast message="X" duration={5000} onClose={onClose} />);

    unmount();
    act(() => {
      vi.advanceTimersByTime(5000);
    });
    expect(onClose).not.toHaveBeenCalled();
  });
});

describe("showToast", () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("renders toast in document body and auto-removes after duration", () => {
    act(() => {
      showToast("Done", 1000);
    });
    expect(document.body.textContent).toContain("Done");

    act(() => {
      vi.advanceTimersByTime(1000);
    });
    expect(document.body.textContent).not.toContain("Done");
  });
});
