import React, { useState, useEffect } from "react";
import { createPortal } from "react-dom";
import { useChatContext } from "../../state/ChatContext";

type ModeOption = { name: string; displayName: string };

export function Footer(): React.ReactElement {
  const { state, send } = useChatContext();
  const [mode, setMode] = useState("");
  const [modes, setModes] = useState<ModeOption[]>([]);
  const targetEl = document.querySelector("footer");
  if (!targetEl) return React.createElement("div");

  useEffect(() => {
    fetch("/api/modes")
      .then((res) => res.json())
      .then((data: ModeOption[]) => {
        if (data.length > 0) {
          setModes(data);
          if (!data.some((m) => m.name === mode)) {
            setMode(data[0].name);
          }
        }
      })
      .catch(() => {
        // modes stays empty, Start and select remain disabled
      });
  }, []);

  const isActive = state.sessionStatus === "active";
  const noModes = modes.length === 0;

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
          disabled: isActive || noModes,
          onChange: (e: React.ChangeEvent<HTMLSelectElement>) =>
            setMode(e.target.value),
        },
        ...modes.map((m) =>
          React.createElement("option", { key: m.name, value: m.name }, m.displayName)
        )
      ),
      React.createElement(
        "button",
        {
          "data-testid": "start-btn",
          id: "startBtn",
          className: "btn btn-primary",
          disabled: isActive || noModes,
          onClick: () => send({ type: "START_SESSION", mode }),
        },
        "Start"
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
        "End"
      )
    ),
    targetEl
  );
}
