import { describe, it, expect, vi } from "vitest";
import { render, fireEvent, screen } from "@testing-library/react";
import { Modal } from "../../shared/Modal";

describe("Modal", () => {
  it("renders nothing when open is false", () => {
    const { container } = render(
      <Modal open={false} title="Test" onClose={vi.fn()}>
        Content
      </Modal>
    );
    expect(container.innerHTML).toBe("");
  });

  it("renders title, children, and save/cancel buttons when open", () => {
    render(
      <Modal open={true} title="Edit Item" onClose={vi.fn()} onSave={vi.fn()}>
        <input placeholder="name" />
      </Modal>
    );
    expect(screen.getByText("Edit Item")).toBeInTheDocument();
    expect(screen.getByPlaceholderText("name")).toBeInTheDocument();
    expect(screen.getByTestId("modal-cancel")).toBeInTheDocument();
    expect(screen.getByTestId("modal-save")).toBeInTheDocument();
  });

  it("calls onClose when Cancel is clicked", () => {
    const onClose = vi.fn();
    render(<Modal open={true} title="X" onClose={onClose}>Body</Modal>);
    fireEvent.click(screen.getByTestId("modal-cancel"));
    expect(onClose).toHaveBeenCalledOnce();
  });

  it("calls onClose when overlay backdrop is clicked", () => {
    const onClose = vi.fn();
    render(<Modal open={true} title="X" onClose={onClose}>Body</Modal>);
    fireEvent.click(screen.getByTestId("modal-overlay"));
    expect(onClose).toHaveBeenCalledOnce();
  });

  it("calls onSave when Save is clicked", () => {
    const onSave = vi.fn();
    render(<Modal open={true} title="X" onClose={vi.fn()} onSave={onSave}>Body</Modal>);
    fireEvent.click(screen.getByTestId("modal-save"));
    expect(onSave).toHaveBeenCalledOnce();
  });

  it("does not render Save button when onSave is not provided", () => {
    render(<Modal open={true} title="X" onClose={vi.fn()}>Body</Modal>);
    expect(screen.queryByTestId("modal-save")).toBeNull();
  });
});
