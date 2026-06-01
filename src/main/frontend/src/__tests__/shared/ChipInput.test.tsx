import { describe, it, expect, vi } from "vitest";
import { render, fireEvent, screen } from "@testing-library/react";
import { ChipInput } from "../../shared/ChipInput";
import type { Tag } from "../../shared/useTagAutocomplete";

describe("ChipInput", () => {
  const tags: Tag[] = [
    { id: 1, name: "work", type: "deck" },
    { id: 2, name: "vocab", type: null },
    { id: 3, name: "hobby", type: null },
  ];

  it("renders selected chips and input", () => {
    const selected: Tag[] = [{ id: 1, name: "work", type: "deck" }];
    render(
      <ChipInput
        options={tags}
        value={selected}
        onChange={vi.fn()}
        placeholder="Search tags..."
      />
    );
    expect(screen.getByTestId("chip").textContent).toContain("work");
    expect(screen.getByPlaceholderText("Search tags...")).toBeInTheDocument();
  });

  it("shows suggestions when typing and clicking one calls onChange", () => {
    const onChange = vi.fn();
    render(<ChipInput options={tags} value={[]} onChange={onChange} />);

    const input = screen.getByTestId("chip-input-field");
    fireEvent.change(input, { target: { value: "vo" } });

    const suggestion = screen.getByText("vocab");
    fireEvent.click(suggestion);

    expect(onChange).toHaveBeenCalledWith([{ id: 2, name: "vocab", type: null }]);
  });

  it("removes chip when × is clicked", () => {
    const onChange = vi.fn();
    const selected: Tag[] = [
      { id: 1, name: "work", type: "deck" },
      { id: 2, name: "vocab", type: null },
    ];
    render(<ChipInput options={tags} value={selected} onChange={onChange} />);

    fireEvent.click(screen.getAllByTestId("chip-remove")[0]);
    expect(onChange).toHaveBeenCalledWith([{ id: 2, name: "vocab", type: null }]);
  });

  it("removes last chip on backspace when input is empty", () => {
    const onChange = vi.fn();
    const selected: Tag[] = [{ id: 1, name: "work", type: "deck" }];
    render(<ChipInput options={tags} value={selected} onChange={onChange} />);

    const input = screen.getByTestId("chip-input-field");
    fireEvent.keyDown(input, { key: "Backspace" });

    expect(onChange).toHaveBeenCalledWith([]);
  });

  it("does not add duplicate tag", () => {
    const onChange = vi.fn();
    const selected: Tag[] = [{ id: 2, name: "vocab", type: null }];
    render(<ChipInput options={tags} value={selected} onChange={onChange} />);

    const input = screen.getByTestId("chip-input-field");
    fireEvent.change(input, { target: { value: "vocab" } });

    const suggestions = screen.queryAllByTestId("chip-suggestion");
    expect(suggestions).toHaveLength(0);
  });
});
