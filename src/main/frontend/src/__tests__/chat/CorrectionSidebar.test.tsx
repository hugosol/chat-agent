import { describe, it, expect, vi } from "vitest";
import { render, fireEvent } from "@testing-library/react";
import { CorrectionSidebar } from "../../components/chat/CorrectionSidebar/CorrectionSidebar";

describe("CorrectionSidebar", () => {
  it("renders placeholder text when corrections list is empty", () => {
    const { getByTestId } = render(
      <CorrectionSidebar corrections={[]} isOpen={false} onToggle={() => {}} />
    );
    expect(getByTestId("correction-sidebar-empty").textContent).toBe("No corrections yet.");
  });

  it("renders type, original, arrow, and corrected text for a single correction", () => {
    const corrections = [
      { type: "GRAMMAR" as const, original: "I go", corrected: "I went", explanation: "", messageId: 1 },
    ];
    const { getByTestId, getAllByTestId } = render(
      <CorrectionSidebar corrections={corrections} isOpen={false} onToggle={() => {}} />
    );
    const items = getAllByTestId("correction-item");
    expect(items).toHaveLength(1);
    expect(items[0].textContent).toContain("GRAMMAR");
    expect(items[0].textContent).toContain("I go");
    expect(items[0].textContent).toContain("I went");
  });

  it("renders multiple corrections in order", () => {
    const corrections = [
      { type: "GRAMMAR" as const, original: "I go", corrected: "I went", explanation: "", messageId: 1 },
      { type: "WORD_CHOICE" as const, original: "big", corrected: "large", explanation: "", messageId: 1 },
    ];
    const { getAllByTestId } = render(
      <CorrectionSidebar corrections={corrections} isOpen={false} onToggle={() => {}} />
    );
    const items = getAllByTestId("correction-item");
    expect(items).toHaveLength(2);
    expect(items[0].textContent).toContain("GRAMMAR");
    expect(items[1].textContent).toContain("WORD_CHOICE");
  });

  it("renders explanation text when provided", () => {
    const corrections = [
      { type: "CHINGLISH" as const, original: "open light", corrected: "turn on light", explanation: "Use 'turn on' for devices", messageId: 1 },
    ];
    const { getByTestId } = render(
      <CorrectionSidebar corrections={corrections} isOpen={false} onToggle={() => {}} />
    );
    expect(getByTestId("correction-item").textContent).toContain("Use 'turn on' for devices");
  });

  it("shows aria-expanded false when collapsed is true", () => {
    const { getByTestId } = render(
      <CorrectionSidebar corrections={[]} isOpen={false} onToggle={() => {}} />
    );
    expect(getByTestId("correction-sidebar").getAttribute("aria-expanded")).toBe("false");
  });

  it("shows aria-expanded true when collapsed is false", () => {
    const { getByTestId } = render(
      <CorrectionSidebar corrections={[]} isOpen={true} onToggle={() => {}} />
    );
    expect(getByTestId("correction-sidebar").getAttribute("aria-expanded")).toBe("true");
  });

  it("shows correction count on badge", () => {
    const corrections = [
      { type: "GRAMMAR" as const, original: "a", corrected: "b", explanation: "", messageId: 1 },
      { type: "GRAMMAR" as const, original: "c", corrected: "d", explanation: "", messageId: 2 },
    ];
    const { getByTestId } = render(
      <CorrectionSidebar corrections={corrections} isOpen={false} onToggle={() => {}} />
    );
    expect(getByTestId("correction-badge").textContent).toBe("2");
  });

  it("hides badge when correction count is zero", () => {
    const { queryByTestId } = render(
      <CorrectionSidebar corrections={[]} isOpen={false} onToggle={() => {}} />
    );
    expect(queryByTestId("correction-badge")).toBeNull();
  });

  it("calls onToggle when toggle button is clicked", () => {
    const onToggle = vi.fn();
    const corrections = [
      { type: "GRAMMAR" as const, original: "a", corrected: "b", explanation: "", messageId: 1 },
    ];
    const { getByTestId } = render(
      <CorrectionSidebar corrections={corrections} isOpen={false} onToggle={onToggle} />
    );
    fireEvent.click(getByTestId("correction-toggle"));
    expect(onToggle).toHaveBeenCalledTimes(1);
  });

  it("calls onToggle when close button is clicked", () => {
    const onToggle = vi.fn();
    const { getByTestId } = render(
      <CorrectionSidebar corrections={[]} isOpen={true} onToggle={onToggle} />
    );
    fireEvent.click(getByTestId("correction-sidebar-close"));
    expect(onToggle).toHaveBeenCalledTimes(1);
  });
});
