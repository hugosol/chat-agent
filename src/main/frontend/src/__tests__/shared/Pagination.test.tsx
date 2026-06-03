import { describe, it, expect, vi } from "vitest";
import { render, fireEvent, screen } from "@testing-library/react";
import { Pagination } from "../../shared/Pagination";

describe("Pagination", () => {
  it("renders nothing when totalPages <= 1", () => {
    const { container } = render(
      <Pagination page={0} totalPages={1} onPageChange={vi.fn()} />
    );
    expect(container.innerHTML).toBe("");
  });

  it("renders prev/next buttons and page numbers when totalPages > 1", () => {
    render(<Pagination page={0} totalPages={3} onPageChange={vi.fn()} />);
    expect(screen.getByTestId("page-prev")).toBeInTheDocument();
    expect(screen.getByTestId("page-next")).toBeInTheDocument();
    expect(screen.getAllByTestId("page-num")).toHaveLength(3);
  });

  it("disables prev button on first page", () => {
    render(<Pagination page={0} totalPages={3} onPageChange={vi.fn()} />);
    expect(screen.getByTestId("page-prev")).toBeDisabled();
  });

  it("disables next button on last page", () => {
    render(<Pagination page={2} totalPages={3} onPageChange={vi.fn()} />);
    expect(screen.getByTestId("page-next")).toBeDisabled();
  });

  it("highlights active page with data-active='true'", () => {
    render(<Pagination page={1} totalPages={3} onPageChange={vi.fn()} />);
    const nums = screen.getAllByTestId("page-num");
    expect(nums[0].getAttribute("data-active")).toBe("false");
    expect(nums[1].getAttribute("data-active")).toBe("true");
    expect(nums[2].getAttribute("data-active")).toBe("false");
  });

  it("calls onPageChange with correct page when a page number is clicked", () => {
    const onPageChange = vi.fn();
    render(<Pagination page={0} totalPages={3} onPageChange={onPageChange} />);
    fireEvent.click(screen.getAllByTestId("page-num")[2]);
    expect(onPageChange).toHaveBeenCalledWith(2);
  });

  it("calls onPageChange with page-1 when prev is clicked", () => {
    const onPageChange = vi.fn();
    render(<Pagination page={1} totalPages={3} onPageChange={onPageChange} />);
    fireEvent.click(screen.getByTestId("page-prev"));
    expect(onPageChange).toHaveBeenCalledWith(0);
  });

  it("calls onPageChange with page+1 when next is clicked", () => {
    const onPageChange = vi.fn();
    render(<Pagination page={0} totalPages={3} onPageChange={onPageChange} />);
    fireEvent.click(screen.getByTestId("page-next"));
    expect(onPageChange).toHaveBeenCalledWith(1);
  });

  it("shows ellipsis for many pages", () => {
    render(<Pagination page={5} totalPages={20} onPageChange={vi.fn()} />);
    expect(screen.getByTestId("page-ellipsis")).toBeInTheDocument();
  });
});
