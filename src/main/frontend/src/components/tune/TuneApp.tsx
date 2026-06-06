import { useState, useEffect, useCallback } from "react";
import { Header } from "../Header/Header";
import classes from "./TuneApp.module.css";

interface ReviewCount {
  count: number;
  threshold: number;
}

interface LogEntry {
  id: string;
  triggerType: string;
  status: string;
  nonSameDayReviews?: number;
  rescheduledCards?: number;
  finalLoss?: number;
  defaultLoss?: number;
  durationMs: number;
  startTime: string;
}

interface PageResponse {
  content: LogEntry[];
  totalPages: number;
  number: number;
}

function TuneApp(): JSX.Element {
  const [userId, setUserId] = useState<string>("");
  const [isAdmin, setIsAdmin] = useState(false);
  const [reviewCount, setReviewCount] = useState<ReviewCount | null>(null);
  const [optimizeLogs, setOptimizeLogs] = useState<LogEntry[]>([]);
  const [optPage, setOptPage] = useState(0);
  const [optTotalPages, setOptTotalPages] = useState(0);
  const [rescheduleLogs, setRescheduleLogs] = useState<LogEntry[]>([]);
  const [resPage, setResPage] = useState(0);
  const [resTotalPages, setResTotalPages] = useState(0);
  const [allUsers, setAllUsers] = useState<string[]>([]);
  const [loadingUsers, setLoadingUsers] = useState(false);

  useEffect(() => {
    fetch("/api/user/me")
      .then((r) => r.json())
      .then((data) => {
        setUserId(data.username);
        setIsAdmin(data.admin);
      });
  }, []);

  useEffect(() => {
    if (isAdmin) {
      setLoadingUsers(true);
      fetch("/api/admin/users")
        .then((r) => r.json())
        .then((data: { username: string }[]) => {
          setAllUsers([userId, ...data.map((u) => u.username)]);
        })
        .catch(() => setAllUsers([]))
        .finally(() => setLoadingUsers(false));
    }
  }, [isAdmin]);

  useEffect(() => {
    if (!userId) return;
    fetch(`/api/tune/review-count?userId=${encodeURIComponent(userId)}`)
      .then((r) => r.json())
      .then(setReviewCount);
    loadOptimizeLogs(0);
    loadRescheduleLogs(0);
  }, [userId]);

  const loadOptimizeLogs = useCallback((page: number) => {
    if (!userId) return;
    fetch(`/api/tune/optimize-logs?userId=${encodeURIComponent(userId)}&page=${page}`)
      .then((r) => r.json())
      .then((data: PageResponse) => {
        setOptimizeLogs(data.content);
        setOptPage(data.number);
        setOptTotalPages(data.totalPages);
      });
  }, [userId]);

  const loadRescheduleLogs = useCallback((page: number) => {
    if (!userId) return;
    fetch(`/api/tune/reschedule-logs?userId=${encodeURIComponent(userId)}&page=${page}`)
      .then((r) => r.json())
      .then((data: PageResponse) => {
        setRescheduleLogs(data.content);
        setResPage(data.number);
        setResTotalPages(data.totalPages);
      });
  }, [userId]);

  const formatTime = (t: string) => {
    const d = new Date(t);
    return d.toLocaleString();
  };

  return (
    <div>
      <Header />
    <div className={classes.root}>
      <div className={classes.topBar}>
        <span className={classes.title}>Tune</span>
        {isAdmin && (
          <select
            className={classes.userSelect}
            value={userId}
            onChange={(e) => setUserId(e.target.value)}
            disabled={loadingUsers}
          >
            {allUsers.map((u) => (
              <option key={u} value={u}>{u}</option>
            ))}
          </select>
        )}
      </div>

      {reviewCount && (
        <div className={classes.countSection}>
          <span className={classes.countLabel}>ReviewLogs</span>
          <span className={classes.countValue}>
            {reviewCount.count} / {reviewCount.threshold}
          </span>
        </div>
      )}

      <div className={classes.section}>
        <h3>Optimize Logs</h3>
        <table className={classes.table}>
          <thead>
            <tr>
              <th>#</th>
              <th>Trigger</th>
              <th>Status</th>
              <th>Data</th>
              <th>Loss</th>
              <th>Time</th>
            </tr>
          </thead>
          <tbody>
            {optimizeLogs.map((log, i) => (
              <tr key={log.id}>
                <td>{optPage * 4 + i + 1}</td>
                <td>{log.triggerType}</td>
                <td>{log.status}</td>
                <td>{log.nonSameDayReviews ?? "-"}</td>
                <td>
                  {log.finalLoss != null && log.defaultLoss != null
                    ? `${log.finalLoss.toFixed(4)} / ${log.defaultLoss.toFixed(4)}`
                    : "-"}
                </td>
                <td>{formatTime(log.startTime)}</td>
              </tr>
            ))}
            {optimizeLogs.length === 0 && (
              <tr><td colSpan={6} className={classes.empty}>No logs</td></tr>
            )}
          </tbody>
        </table>
        {optTotalPages > 1 && (
          <div className={classes.pager}>
            <button disabled={optPage === 0} onClick={() => loadOptimizeLogs(optPage - 1)}>Prev</button>
            <span>{optPage + 1} / {optTotalPages}</span>
            <button disabled={optPage >= optTotalPages - 1} onClick={() => loadOptimizeLogs(optPage + 1)}>Next</button>
          </div>
        )}
      </div>

      <div className={classes.section}>
        <h3>Reschedule Logs</h3>
        <table className={classes.table}>
          <thead>
            <tr>
              <th>#</th>
              <th>Trigger</th>
              <th>Status</th>
              <th>Cards</th>
              <th>Time</th>
            </tr>
          </thead>
          <tbody>
            {rescheduleLogs.map((log, i) => (
              <tr key={log.id}>
                <td>{resPage * 4 + i + 1}</td>
                <td>{log.triggerType}</td>
                <td>{log.status}</td>
                <td>{log.rescheduledCards ?? "-"}</td>
                <td>{formatTime(log.startTime)}</td>
              </tr>
            ))}
            {rescheduleLogs.length === 0 && (
              <tr><td colSpan={5} className={classes.empty}>No logs</td></tr>
            )}
          </tbody>
        </table>
        {resTotalPages > 1 && (
          <div className={classes.pager}>
            <button disabled={resPage === 0} onClick={() => loadRescheduleLogs(resPage - 1)}>Prev</button>
            <span>{resPage + 1} / {resTotalPages}</span>
            <button disabled={resPage >= resTotalPages - 1} onClick={() => loadRescheduleLogs(resPage + 1)}>Next</button>
          </div>
        )}
      </div>
    </div>
    </div>
  );
}

export { TuneApp };
