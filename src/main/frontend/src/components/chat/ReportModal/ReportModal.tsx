import React from "react";
import { useChatContext } from "../../../state/ChatContext";
import styles from "./ReportModal.module.css";

export function ReportModal(): React.ReactElement | null {
  const { state, dispatch } = useChatContext();

  if (!state.report) return null;

  const report = state.report;
  const assessment = (report.overallAssessment as string) || (report.summary as string) || "";
  const fluencyScore = report.fluencyScore as number | undefined;
  const errorSummary = report.errorSummary as string ?? "";
  const keyTakeaway = report.keyTakeaway as string ?? "";

  function handleClose(): void {
    dispatch({ type: "DISMISS_REPORT" });
  }

  return React.createElement(
    "div",
    {
      "data-testid": "report-modal",
      className: styles.overlay,
      "aria-hidden": "false",
    },
    React.createElement(
      "div",
      { className: styles.modal },
      React.createElement("h2", { className: styles.title }, "Session Report"),
      React.createElement(
        "div",
        { "data-testid": "report-content" },
        assessment &&
          React.createElement("div", { className: styles.section },
            React.createElement("strong", { className: styles.sectionLabel }, "Overall Assessment"),
            React.createElement("br"),
            assessment
          ),
        fluencyScore !== undefined && fluencyScore >= 0 &&
          React.createElement("div", { className: styles.section },
            React.createElement("strong", { className: styles.sectionLabel }, "Fluency Score"),
            React.createElement("br"),
            fluencyScore
          ),
        errorSummary &&
          React.createElement("div", { className: styles.section },
            React.createElement("strong", { className: styles.sectionLabel }, "Error Summary"),
            React.createElement("br"),
            errorSummary
          ),
        keyTakeaway &&
          React.createElement("div", { className: styles.section },
            React.createElement("strong", { className: styles.sectionLabel }, "Key Takeaway"),
            React.createElement("br"),
            keyTakeaway
          )
      ),
      React.createElement(
        "button",
        {
          "data-testid": "report-close-btn",
          className: styles.closeBtn,
          onClick: handleClose,
        },
        "Close"
      )
    )
  );
}
