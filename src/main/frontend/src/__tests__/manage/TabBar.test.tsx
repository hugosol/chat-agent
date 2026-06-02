import { describe, it, expect, vi } from "vitest";
import { render, fireEvent, screen } from "@testing-library/react";
import { TabBar } from "../../components/manage/TabBar";

describe("TabBar", () => {
  it("renders Cards and Tags tabs", () => {
    render(<TabBar activeTab="cards" onTabChange={vi.fn()} />);
    expect(screen.getByTestId("tab-cards")).toBeInTheDocument();
    expect(screen.getByTestId("tab-tags")).toBeInTheDocument();
  });

  it("highlights active tab with data-active", () => {
    render(<TabBar activeTab="cards" onTabChange={vi.fn()} />);
    expect(screen.getByTestId("tab-cards").getAttribute("data-active")).toBe("true");
    expect(screen.getByTestId("tab-tags").getAttribute("data-active")).toBe("false");
  });

  it("calls onTabChange when a tab is clicked", () => {
    const onTabChange = vi.fn();
    render(<TabBar activeTab="cards" onTabChange={onTabChange} />);
    fireEvent.click(screen.getByTestId("tab-tags"));
    expect(onTabChange).toHaveBeenCalledWith("tags");
  });
});
