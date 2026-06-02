import type { Tag } from "../../shared/types";
import styles from "./TagTable.module.css";

interface TagTableProps {
  tags: Tag[];
  editingId: number | null;
  editName: string;
  editIsDeck: boolean;
  onEdit: (tag: Tag) => void;
  onEditNameChange: (name: string) => void;
  onEditIsDeckChange: (isDeck: boolean) => void;
  onSave: () => void;
  onCancel: () => void;
  onDelete: (tag: Tag) => void;
}

function TagTable({
  tags,
  editingId,
  editName,
  editIsDeck,
  onEdit,
  onEditNameChange,
  onEditIsDeckChange,
  onSave,
  onCancel,
  onDelete,
}: TagTableProps): JSX.Element {
  if (tags.length === 0) {
    return <div className={styles.empty} data-testid="empty-state">暂无标签</div>;
  }

  return (
    <table className={styles.table} data-testid="tag-table">
      <thead>
        <tr>
          <th className={styles.th}>Name</th>
          <th className={styles.th}>Deck</th>
          <th className={styles.th}></th>
        </tr>
      </thead>
      <tbody>
        {tags.map((tag) =>
          tag.id === editingId ? (
            <tr key={tag.id} data-id={tag.id} data-testid="tag-edit-row">
              <td className={styles.td}>
                <input
                  type="text"
                  className={styles.td + " input"}
                  data-testid="edit-name-input"
                  value={editName}
                  onChange={(e) => onEditNameChange(e.target.value)}
                />
              </td>
              <td className={styles.td}>
                <input type="checkbox" checked={editIsDeck} onChange={(e) => onEditIsDeckChange(e.target.checked)} />
              </td>
              <td className={`${styles.td} ${styles.actions}`}>
                <button className={styles.saveBtn} data-testid="btn-save-tag" onClick={onSave}>
                  Save
                </button>
                <button data-testid="btn-cancel-tag" onClick={onCancel}>
                  Cancel
                </button>
              </td>
            </tr>
          ) : (
            <tr key={tag.id} data-id={tag.id} data-name={tag.name}>
              <td className={styles.td}>{tag.name}</td>
              <td className={styles.td}>
                <input type="checkbox" disabled checked={tag.type === "deck"} readOnly />
              </td>
              <td className={`${styles.td} ${styles.actions}`}>
                <button data-testid="btn-edit-tag" onClick={() => onEdit(tag)}>
                  Edit
                </button>
                <button data-testid="btn-delete-tag" onClick={() => onDelete(tag)}>
                  Delete
                </button>
              </td>
            </tr>
          )
        )}
      </tbody>
    </table>
  );
}

export { TagTable };
export type { TagTableProps };
