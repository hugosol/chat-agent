import React, { useState, useEffect } from "react";
import { subscribeDebug, type DebugEntry } from "../../shared/debugLog";
import styles from "./DebugPanel.module.css";

interface DebugPanelProps {
  isOpen: boolean;
  onToggle: () => void;
}

export function DebugPanel({ isOpen, onToggle }: DebugPanelProps): React.ReactElement {
  const [entries, setEntries] = useState<DebugEntry[]>([]);

  useEffect(() => {
    const unsub = subscribeDebug((entry: DebugEntry) => {
      setEntries((prev) => [...prev, entry]);
    });
    return unsub;
  }, []);

  function handleClear(): void {
    setEntries([]);
  }

  return React.createElement(
    "div",
    { className: styles.debugPanel },
    React.createElement(
      "div",
      { style: { display: "flex", gap: 6, padding: "4px 8px", borderBottom: "1px solid #333", flexShrink: 0 } },
      React.createElement("button", { onClick: onToggle, className: styles.toggleBtn }, "Log"),
      isOpen &&
        React.createElement("button", { onClick: handleClear, className: styles.clearBtn }, "Clear")
    ),
    isOpen &&
      React.createElement(
        "div",
        { className: styles.logList },
        ...entries.map((entry, i) =>
          React.createElement(
            "div",
            { key: i, className: styles.logEntry },
            React.createElement(
              "span",
              { className: styles.timestamp },
              entry.timestamp.toLocaleTimeString()
            ),
            React.createElement(
              "span",
              { className: styles.message },
              entry.message
            )
          )
        )
      )
  );
}
