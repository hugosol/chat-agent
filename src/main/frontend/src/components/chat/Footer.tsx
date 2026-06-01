import React, { useState } from "react";
import { createPortal } from "react-dom";
import { useChatContext } from "../../state/ChatContext";

export function Footer(): React.ReactElement {
  const { state, send } = useChatContext();
  const [mode, setMode] = useState("WORKPLACE_STANDUP");
  const targetEl = document.querySelector("footer");
  if (!targetEl) return React.createElement("div");

  const isActive = state.sessionStatus === "active";

  return createPortal(
    React.createElement(
      "div",
      { className: "controls" },
      React.createElement(
        "select",
        {
          "data-testid": "mode-select",
          id: "modeSelect",
          value: mode,
          disabled: isActive,
          onChange: (e: React.ChangeEvent<HTMLSelectElement>) =>
            setMode(e.target.value),
        },
        React.createElement("option", { value: "WORKPLACE_STANDUP" }, "Workplace Standup"),
        React.createElement("option", { value: "DAILY_TALK" }, "Daily Talk")
      ),
      React.createElement(
        "button",
        {
          "data-testid": "start-btn",
          id: "startBtn",
          className: "btn btn-primary",
          disabled: isActive,
          onClick: () => send({ type: "START_SESSION", mode }),
        },
        "Start Session"
      ),
      React.createElement(
        "button",
        {
          "data-testid": "end-btn",
          id: "endBtn",
          className: "btn btn-danger",
          disabled: !isActive,
          onClick: () => send({ type: "END_SESSION" }),
        },
        "End & Report"
      )
    ),
    targetEl
  );
}
