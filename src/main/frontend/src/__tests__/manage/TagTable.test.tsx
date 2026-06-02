import { describe, it, expect, vi } from "vitest";
import { render, fireEvent, screen } from "@testing-library/react";
import { TagTable } from "../../components/manage/TagTable";
import type { Tag } from "../../shared/types";

const sampleTags: Tag[] = [
  { id: 1, name: "Daily English", type: "deck" },
  { id: 2, name: "verb", type: null },
];

describe("TagTable", () => {
  it("renders empty state when no tags", () => {
    render(
      <TagTable
        tags={[]}
        editingId={null}
        editName=""
        editIsDeck={false}
        onEdit={vi.fn()}
        onSave={vi.fn()}
        onCancel={vi.fn()}
        onDelete={vi.fn()}
      />
    );
    expect(screen.getByText("暂无标签")).toBeInTheDocument();
  });

  it("renders all tags in table", () => {
    render(
      <TagTable
        tags={sampleTags}
        editingId={null}
        editName=""
        editIsDeck={false}
        onEdit={vi.fn()}
        onSave={vi.fn()}
        onCancel={vi.fn()}
        onDelete={vi.fn()}
      />
    );
    expect(screen.getByText("Daily English")).toBeInTheDocument();
    expect(screen.getByText("verb")).toBeInTheDocument();
  });

  it("renders deck checkbox checked for deck tags", () => {
    render(
      <TagTable
        tags={sampleTags}
        editingId={null}
        editName=""
        editIsDeck={false}
        onEdit={vi.fn()}
        onSave={vi.fn()}
        onCancel={vi.fn()}
        onDelete={vi.fn()}
      />
    );
    const checkboxes = screen.getAllByRole("checkbox");
    expect(checkboxes[0]).toBeChecked();
    expect(checkboxes[1]).not.toBeChecked();
  });

  it("calls onEdit with tag when Edit button clicked", () => {
    const onEdit = vi.fn();
    render(
      <TagTable
        tags={sampleTags}
        editingId={null}
        editName=""
        editIsDeck={false}
        onEdit={onEdit}
        onSave={vi.fn()}
        onCancel={vi.fn()}
        onDelete={vi.fn()}
      />
    );
    fireEvent.click(screen.getAllByTestId("btn-edit-tag")[0]);
    expect(onEdit).toHaveBeenCalledWith(sampleTags[0]);
  });

  it("renders edit inputs when editingId matches", () => {
    render(
      <TagTable
        tags={sampleTags}
        editingId={1}
        editName="Daily English"
        editIsDeck={true}
        onEdit={vi.fn()}
        onSave={vi.fn()}
        onCancel={vi.fn()}
        onDelete={vi.fn()}
      />
    );
    expect(screen.getByTestId("edit-name-input")).toBeInTheDocument();
    expect(screen.getByTestId("btn-save-tag")).toBeInTheDocument();
    expect(screen.getByTestId("btn-cancel-tag")).toBeInTheDocument();
  });

  it("calls onSave when Save clicked", () => {
    const onSave = vi.fn();
    render(
      <TagTable
        tags={sampleTags}
        editingId={1}
        editName="Daily English"
        editIsDeck={true}
        onEdit={vi.fn()}
        onSave={onSave}
        onCancel={vi.fn()}
        onDelete={vi.fn()}
      />
    );
    fireEvent.click(screen.getByTestId("btn-save-tag"));
    expect(onSave).toHaveBeenCalledOnce();
  });

  it("calls onCancel when Cancel clicked", () => {
    const onCancel = vi.fn();
    render(
      <TagTable
        tags={sampleTags}
        editingId={1}
        editName="Daily English"
        editIsDeck={false}
        onEdit={vi.fn()}
        onSave={vi.fn()}
        onCancel={onCancel}
        onDelete={vi.fn()}
      />
    );
    fireEvent.click(screen.getByTestId("btn-cancel-tag"));
    expect(onCancel).toHaveBeenCalledOnce();
  });

  it("calls onDelete when Delete button clicked", () => {
    const onDelete = vi.fn();
    render(
      <TagTable
        tags={sampleTags}
        editingId={null}
        editName=""
        editIsDeck={false}
        onEdit={vi.fn()}
        onSave={vi.fn()}
        onCancel={vi.fn()}
        onDelete={onDelete}
      />
    );
    fireEvent.click(screen.getAllByTestId("btn-delete-tag")[1]);
    expect(onDelete).toHaveBeenCalledWith(sampleTags[1]);
  });
});
