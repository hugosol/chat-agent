import { useState, useRef } from "react";
import { Modal } from "../../shared/Modal";

interface MovieImportModalProps {
  open: boolean;
  onClose: () => void;
  onImported: () => void;
}

interface ImportError {
  row: number;
  reason: string;
}

type Stage = "select" | "parsing" | "uploading" | "result";

export function MovieImportModal({ open, onClose, onImported }: MovieImportModalProps): JSX.Element {
  const [stage, setStage] = useState<Stage>("select");
  const [fileName, setFileName] = useState("");
  const [validRows, setValidRows] = useState<Array<{ imdbId: string; title: string; year: string }>>([]);
  const [errors, setErrors] = useState<ImportError[]>([]);
  const [importedCount, setImportedCount] = useState(0);
  const [uploadError, setUploadError] = useState<string | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const reset = () => {
    setStage("select");
    setFileName("");
    setValidRows([]);
    setErrors([]);
    setImportedCount(0);
    setUploadError(null);
    if (fileInputRef.current) fileInputRef.current.value = "";
  };

  const handleClose = () => {
    reset();
    onClose();
  };

  const handleFileSelected = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    setFileName(file.name);
    setStage("parsing");

    const reader = new FileReader();
    reader.onload = () => {
      const text = reader.result as string;
      const lines = text.split(/\r?\n/).filter((line) => line.trim());

      if (lines.length === 0) {
        setErrors([{ row: 0, reason: "文件为空" }]);
        setStage("select");
        return;
      }

      let startIndex = 0;
      const firstCol = lines[0].split(",")[0]?.trim();
      // Header detection: skip if first column is non-numeric (does not start with tt)
      if (firstCol && !/^tt\d+$/i.test(firstCol)) {
        startIndex = 1;
      }

      const parsed: typeof validRows = [];
      const parseErrors: ImportError[] = [];

      for (let i = startIndex; i < lines.length; i++) {
        const rowNum = i + 1;
        const cols = lines[i].split(",");
        const imdbId = cols[0]?.trim() ?? "";
        const title = cols[1]?.trim() ?? "";
        const year = cols[2]?.trim() ?? "";

        if (!imdbId || !/^tt\d+$/i.test(imdbId)) {
          parseErrors.push({ row: rowNum, reason: `imdbId 格式无效: "${imdbId}"` });
          continue;
        }
        if (!title) {
          parseErrors.push({ row: rowNum, reason: "标题为空" });
          continue;
        }
        if (year && isNaN(Number(year))) {
          parseErrors.push({ row: rowNum, reason: `年份格式无效: "${year}"` });
          continue;
        }

        parsed.push({ imdbId, title, year: year || "" });
      }

      if (parsed.length === 0) {
        setErrors(parseErrors);
        setStage("select");
        return;
      }

      setValidRows(parsed);
      setErrors(parseErrors);
      setStage("select"); // Wait for user to click upload
    };
    reader.onerror = () => {
      setErrors([{ row: 0, reason: "读取文件失败" }]);
      setStage("select");
    };
    reader.readAsText(file);
  };

  const handleUpload = async () => {
    setStage("uploading");
    setUploadError(null);
    try {
      const resp = await fetch("/api/movies/import/batch", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(validRows.map((r) => ({
          imdbId: r.imdbId,
          title: r.title,
          year: r.year || undefined,
        }))),
      });
      if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
      const data = await resp.json();
      setImportedCount(data.imported ?? validRows.length);
      // Backend may have skipped duplicates; track as errors
      if (data.imported < validRows.length) {
        setErrors((prev) => [
          ...prev,
          { row: 0, reason: `${validRows.length - data.imported} 部电影已存在，已跳过` },
        ]);
      }
      setStage("result");
    } catch (e) {
      setUploadError(e instanceof Error ? e.message : "上传失败");
      setStage("select");
    }
  };

  return (
    <Modal open={open} title="批量导入电影" onClose={handleClose}>
      <div data-testid="movie-import-modal">
        {stage === "select" && (
          <div>
            <div style={{ color: "#888", marginBottom: 12, fontSize: "0.85em" }}>
              格式：imdbId,title,year<br />
              tt1375666,Inception,2010<br />
              tt0133093,The Matrix,1999
            </div>
            {fileName && validRows.length > 0 && (
              <div style={{ marginBottom: 12, color: "#e0e0e0" }}>
                {fileName} — 已解析 {validRows.length} 部电影
                {errors.length > 0 && (
                  <span style={{ color: "rgb(231, 76, 60)" }}>，{errors.length} 个错误</span>
                )}
              </div>
            )}
            {errors.length > 0 && (
              <div style={{ marginBottom: 12 }}>
                {errors.map((e, i) => (
                  <div key={i} style={{ color: "rgb(231, 76, 60)", fontSize: "0.85em" }}>
                    {e.row > 0 ? `第 ${e.row} 行: ` : ""}{e.reason}
                  </div>
                ))}
              </div>
            )}
            <input
              ref={fileInputRef}
              type="file"
              accept=".csv"
              data-testid="movie-import-file"
              onChange={handleFileSelected}
            />
            {validRows.length > 0 && (
              <button
                className="btn btn-primary"
                data-testid="movie-import-upload-btn"
                style={{ marginTop: 12 }}
                onClick={handleUpload}
              >
                上传 {validRows.length} 部电影
              </button>
            )}
            {uploadError && (
              <div style={{ color: "rgb(231, 76, 60)", marginTop: 8 }}>{uploadError}</div>
            )}
          </div>
        )}

        {stage === "parsing" && (
          <div data-testid="movie-import-parsing">解析中...</div>
        )}

        {stage === "uploading" && (
          <div data-testid="movie-import-uploading">上传中...</div>
        )}

        {stage === "result" && (
          <div data-testid="movie-import-result">
            <div style={{ color: "rgb(39, 174, 96)", marginBottom: 12 }}>
              成功导入 {importedCount} 部电影
            </div>
            {errors.length > 0 && (
              <div>
                {errors.map((e, i) => (
                  <div key={i} style={{ color: "rgb(231, 76, 60)", fontSize: "0.85em" }}>
                    {e.row > 0 ? `第 ${e.row} 行: ` : ""}{e.reason}
                  </div>
                ))}
              </div>
            )}
            <button
              className="btn btn-primary"
              style={{ marginTop: 12 }}
              onClick={() => {
                reset();
                onImported();
              }}
            >
              完成
            </button>
          </div>
        )}
      </div>
    </Modal>
  );
}
