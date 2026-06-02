import { useState, useEffect, useCallback } from "react";
import type { Tag } from "../../shared/types";
import { TagTable } from "./TagTable";
import { Modal } from "../../shared/Modal";
import { showToast } from "../../shared/Toast";

function TagsTab(): JSX.Element {
  const [tags, setTags] = useState<Tag[]>([]);
  const [loading, setLoading] = useState(true);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [editName, setEditName] = useState("");
  const [editIsDeck, setEditIsDeck] = useState(false);
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [newTagName, setNewTagName] = useState("");
  const [newTagIsDeck, setNewTagIsDeck] = useState(false);
  const [deleteTarget, setDeleteTarget] = useState<Tag | null>(null);

  const loadTags = useCallback(() => {
    fetch("/api/tags", { credentials: "same-origin" })
      .then((r) => r.json())
      .then((data: Tag[]) => {
        setTags(data);
        setLoading(false);
      })
      .catch(() => {
        showToast("加载标签失败");
        setLoading(false);
      });
  }, []);

  useEffect(() => {
    loadTags();
  }, [loadTags]);

  const handleEdit = useCallback((tag: Tag) => {
    setEditingId(tag.id);
    setEditName(tag.name);
    setEditIsDeck(tag.type === "deck");
  }, []);

  const handleSaveEdit = useCallback(() => {
    if (!editingId) return;
    fetch(`/api/tags/${editingId}`, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ name: editName.trim(), type: editIsDeck ? "deck" : null }),
      credentials: "same-origin",
    })
      .then((resp) => {
        if (resp.ok) {
          setEditingId(null);
          loadTags();
          return;
        }
        return resp.json().then((body: any) => {
          throw { message: body?.message || "Update failed", status: resp.status };
        });
      })
      .catch((err: any) => {
        if (err.status === 422) showToast(err.message);
        else showToast("保存失败: " + (err.message || "Unknown error"));
      });
  }, [editingId, editName, editIsDeck, loadTags]);

  const handleCancelEdit = useCallback(() => {
    setEditingId(null);
  }, []);

  const handleDelete = useCallback((tag: Tag) => {
    setDeleteTarget(tag);
  }, []);

  const handleDeleteConfirm = useCallback(() => {
    if (!deleteTarget) return;
    const id = deleteTarget.id;
    fetch(`/api/tags/${id}`, { method: "DELETE", credentials: "same-origin" })
      .then((resp) => {
        if (resp.ok) {
          setDeleteTarget(null);
          loadTags();
          return;
        }
        if (resp.status === 422) {
          return resp.json().then((errorBody: any) => {
            let orphanCount = "?";
            try {
              const msg = errorBody.message || "";
              const parsed = JSON.parse(msg);
              orphanCount = parsed.orphanCount;
            } catch {}
            showToast(orphanCount + " 张卡片将失去所有牌组，无法删除");
            setDeleteTarget(null);
          });
        }
        return resp.json().then((body: any) => {
          throw { message: body?.message || "Delete failed", status: resp.status };
        });
      })
      .catch((err: any) => {
        if (!err.handled) showToast("删除失败: " + (err.message || "Unknown error"));
        setDeleteTarget(null);
      });
  }, [deleteTarget, loadTags]);

  const handleCreateSave = useCallback(() => {
    if (!newTagName.trim()) {
      showToast("标签名不能为空");
      return;
    }
    fetch("/api/tags", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ name: newTagName.trim(), type: newTagIsDeck ? "deck" : null }),
      credentials: "same-origin",
    })
      .then((resp) => {
        if (resp.ok) {
          setShowCreateModal(false);
          setNewTagName("");
          setNewTagIsDeck(false);
          loadTags();
          return;
        }
        return resp.json().then((body: any) => {
          throw { message: body?.message || "Create failed", status: resp.status };
        });
      })
      .catch((err: any) => {
        if (err.status === 422) showToast(err.message);
        else showToast("创建失败: " + (err.message || "Unknown error"));
      });
  }, [newTagName, newTagIsDeck, loadTags]);

  if (loading) {
    return <div className="empty-state">加载中...</div>;
  }

  return (
    <div>
      <TagTable
        tags={tags}
        editingId={editingId}
        editName={editName}
        editIsDeck={editIsDeck}
        onEdit={handleEdit}
        onEditNameChange={setEditName}
        onEditIsDeckChange={setEditIsDeck}
        onSave={handleSaveEdit}
        onCancel={handleCancelEdit}
        onDelete={handleDelete}
      />

      <div style={{ marginTop: "12px", textAlign: "center" }}>
        <button className="btn btn-primary" onClick={() => setShowCreateModal(true)}>
          创建标签
        </button>
      </div>

      {showCreateModal && (
        <Modal
          open={true}
          title="创建标签"
          onClose={() => setShowCreateModal(false)}
          onSave={handleCreateSave}
        >
          <div className="checkbox-row" style={{ marginTop: "8px" }}>
            <input
              type="checkbox"
              id="newTagDeck"
              checked={newTagIsDeck}
              onChange={(e) => setNewTagIsDeck(e.target.checked)}
            />
            <label htmlFor="newTagDeck">作为牌组</label>
          </div>
          <input
            type="text"
            id="newTagName"
            className="new-tag-name"
            placeholder="标签名称"
            value={newTagName}
            onChange={(e) => setNewTagName(e.target.value)}
          />
        </Modal>
      )}

      {deleteTarget && (
        <Modal
          open={true}
          title="确认删除"
          onClose={() => setDeleteTarget(null)}
          onSave={handleDeleteConfirm}
          saveLabel="Delete"
        >
          <p>确定要删除标签 "{deleteTarget.name}" 吗？</p>
        </Modal>
      )}
    </div>
  );
}

export { TagsTab };
