import React from "react";
import { useChatContext } from "../../state/ChatContext";
import { deriveStatus } from "../../shared/utils";
import styles from "./StatusBar.module.css";

export function StatusBar(): JSX.Element {
  const { state } = useChatContext();
  const { message, type } = deriveStatus(state.appStatus, state.statusPayload);

  return React.createElement(
    "div",
    { id: "statusBar", className: `${styles.statusBar} ${styles[type] ?? ""}` },
    React.createElement("span", { className: styles.indicator }, message)
  );
}
