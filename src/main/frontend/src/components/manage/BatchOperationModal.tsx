import { useState, useEffect } from "react";
import type { Tag } from "../../shared/types";
import { showToast } from "../../shared/Toast";
import styles from "./BatchOperationModal.module.css";

interface BatchOperationModalProps {
  mode: "import" | "export";
  onClose: () => void;
  onComplete: () => void;
}

type Stage = "select-tag" | "ready" | "loading" | "result";

interface ImportError {
  row: number;
  front: string;
  reason: string;
}

interface ImportResult {
  totalRows: number;
  successCount: number;
  errors: ImportError[];
}

function BatchOperationModal({ mode, onClose, onComplete }: BatchOperationModalProps): JSX.Element {
  const [stage, setStage] = useState<Stage>("select-tag");
  const [decks, setDecks] = useState<Tag[]>([]);
  const [selectedTagId, setSelectedTagId] = useState("");
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [result, setResult] = useState<ImportResult | null>(null);

  useEffect(() => {
    fetch("/api/tags?type=deck", { credentials: "same-origin" })
      .then((r) => r.json())
      .then((tags: Tag[]) => setDecks(tags))
      .catch(() => showToast("加载牌组失败"));
  }, []);

  const selectedTag = decks.find((d) => d.id === selectedTagId);
  const canProceed = selectedTagId !== "" && (mode === "export" || selectedFile !== null);

  const handleTagSelect = (e: React.ChangeEvent<HTMLSelectElement>) => {
    setSelectedTagId(e.target.value);
    setStage("ready");
  };

  const handleFileSelect = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target.files && e.target.files.length > 0) {
      setSelectedFile(e.target.files[0]);
    }
  };

  const handleImport = async () => {
    if (!selectedFile || !selectedTagId) return;
    setStage("loading");

    const formData = new FormData();
    formData.append("file", selectedFile);
    formData.append("tagId", selectedTagId);

    try {
      const resp = await fetch("/api/cards/import", {
        method: "POST",
        body: formData,
        credentials: "same-origin",
      });
      const data: ImportResult = await resp.json();
      setResult(data);
      setStage("result");
    } catch {
      showToast("导入失败");
      setStage("ready");
    }
  };

  const handleExport = async () => {
    if (!selectedTagId) return;

    try {
      const resp = await fetch(`/api/cards/export?tagId=${encodeURIComponent(selectedTagId)}`, {
        credentials: "same-origin",
      });
      if (!resp.ok) {
        showToast("导出失败");
        return;
      }
      const blob = await resp.blob();
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      const disposition = resp.headers.get("Content-Disposition") || "";
      const match = disposition.match(/filename="?([^";]+)"?/);
      a.download = match ? match[1] : "export.csv";
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      URL.revokeObjectURL(url);
      onClose();
    } catch {
      showToast("导出失败");
    }
  };

  const handleClose = () => {
    if (result) {
      onComplete();
    }
    onClose();
  };

  return (
    <div className="modal" data-testid="modal-overlay" onClick={() => stage !== "loading" && onClose()}>
      <div className="modal-content" data-testid="modal-content" onClick={(e) => e.stopPropagation()}>
        <h2>{mode === "import" ? "导入卡片" : "导出卡片"}</h2>
        <div className="modal-body">
          {(stage === "select-tag" || stage === "ready") && (
            <>
              <div className={styles.field}>
                <label className={styles.label}>选择牌组</label>
                <select
                  className={styles.select}
                  data-testid="batch-tag-select"
                  value={selectedTagId}
                  onChange={handleTagSelect}
                >
                  <option value="">-- 选择牌组 --</option>
                  {decks.map((deck) => (
                    <option key={deck.id} value={deck.id}>
                      {deck.name}
                    </option>
                  ))}
                </select>
              </div>

              {selectedTag && stage === "ready" && (
                <div className={styles.field}>
                  {mode === "import" ? (
                    <>
                      <label className={styles.label}>选择 CSV 文件</label>
                      <input
                        type="file"
                        accept=".csv"
                        data-testid="batch-file-input"
                        onChange={handleFileSelect}
                        className={styles.fileInput}
                      />
                      {selectedFile && (
                        <div className={styles.fileName}>{selectedFile.name}</div>
                      )}
                    </>
                  ) : (
                    <div className={styles.exportInfo}>
                      将导出 "<strong>{selectedTag.name}</strong>" 下的所有卡片
                    </div>
                  )}
                </div>
              )}
            </>
          )}

          {stage === "loading" && (
            <div className={styles.loading} data-testid="batch-loading">
              <div className={styles.spinner} />
              <span>导入中...</span>
            </div>
          )}

          {stage === "result" && result && (
            <div className={styles.result}>
              <div className={`${styles.resultSummary} ${result.errors.length === 0 ? styles.success : styles.partial}`}>
                成功导入 {result.successCount} 张卡片
                {result.errors.length > 0 && (
                  <span>，跳过 {result.errors.length} 张</span>
                )}
              </div>
              {result.errors.length > 0 && (
                <div className={styles.errorTable} data-testid="batch-error-list">
                  <div className={styles.errorHeader}>
                    <span className={styles.errorColRow}>行号</span>
                    <span className={styles.errorColFront}>卡片正面</span>
                    <span className={styles.errorColReason}>原因</span>
                  </div>
                  {result.errors.map((err, i) => (
                    <div key={i} className={styles.errorRow}>
                      <span className={styles.errorColRow}>{err.row}</span>
                      <span className={styles.errorColFront}>{err.front}</span>
                      <span className={styles.errorColReason}>{err.reason}</span>
                    </div>
                  ))}
                </div>
              )}
            </div>
          )}
        </div>
        <div className="modal-actions">
          {stage === "loading" ? (
            <button className="btn btn-cancel" disabled>
              取消
            </button>
          ) : (
            <button className="btn btn-cancel" data-testid="batch-close-btn" onClick={handleClose}>
              {stage === "result" ? "关闭" : "取消"}
            </button>
          )}
          {stage === "ready" && mode === "import" && (
            <button
              className="btn btn-primary btn-save"
              data-testid="batch-import-btn"
              disabled={!canProceed}
              onClick={handleImport}
            >
              导入
            </button>
          )}
          {stage === "ready" && mode === "export" && (
            <button
              className="btn btn-primary btn-save"
              data-testid="batch-export-btn"
              disabled={!canProceed}
              onClick={handleExport}
            >
              导出
            </button>
          )}
        </div>
      </div>
    </div>
  );
}

export { BatchOperationModal };
