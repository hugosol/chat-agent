import React, { useState } from "react";
import { useChatContext } from "../../state/ChatContext";
import { speakText } from "../../shared/tts";
import type { Message } from "../../state/chatState";
import type { CorrectionData } from "../../shared/types";
import styles from "./MessageList.module.css";

const MAX_VISIBLE = 10;

function buildCorrectionSummary(corrections: CorrectionData[]): string {
  return corrections
    .map((c, i) => `${i + 1}. ${c.original} → ${c.corrected}`)
    .join("\n");
}

function renderCorrectionBubble(
  messageId: number,
  corrections: CorrectionData[]
): React.ReactElement | null {
  if (corrections.length === 0) return null;
  const summary = buildCorrectionSummary(corrections);
  return React.createElement(
    "div",
    {
      key: "correction-" + messageId,
      "data-testid": "correction-bubble",
      "data-message-id": messageId,
      className: `${styles.message} ${styles.correctionBubble}`,
    },
    React.createElement(
      "span",
      { "data-testid": "correction-type-label", className: styles.role },
      "Correction:"
    ),
    " ",
    React.createElement(
      "span",
      { "data-testid": "message-content", className: styles.contentText },
      summary
    )
  );
}

function renderMessage(msg: Message): React.ReactElement {
  const roleLabel = msg.role === "user" ? "You:" : "Agent:";
  const children: React.ReactNode[] = [
    React.createElement("span", { key: "role", className: styles.role }, roleLabel),
    React.createElement(
      "span",
      { key: "content", "data-testid": "message-content", className: styles.contentText },
      msg.text
    ),
  ];
  if (msg.role === "agent" && msg.streaming) {
    children.push(
      React.createElement("span", {
        key: "cursor",
        "data-testid": "stream-cursor",
        className: styles.streamCursor,
      }, "|")
    );
  }
  if (msg.role === "agent" && !msg.streaming) {
    children.push(
      React.createElement(
        "button",
        {
          key: "play",
          "data-testid": "play-button",
          className: styles.btnPlay,
          title: "Read aloud",
          onClick: () => speakText(msg.text),
        },
        "\uD83D\uDD0A"
      )
    );
  }
  const roleClass = msg.role === "user" ? styles.messageUser : styles.messageAgent;
  return React.createElement(
    "div",
    {
      key: msg.id + "-" + msg.role,
      "data-testid": "message",
      "data-role": msg.role,
      "data-message-id": msg.id,
      className: `${styles.message} ${roleClass}`,
    },
    ...children
  );
}

function groupCorrections(corrections: CorrectionData[]): Map<number, CorrectionData[]> {
  const groups = new Map<number, CorrectionData[]>();
  for (const c of corrections) {
    const list = groups.get(c.messageId) || [];
    list.push(c);
    groups.set(c.messageId, list);
  }
  return groups;
}

export function MessageList(): React.ReactElement {
  const { state } = useChatContext();
  const [showAll, setShowAll] = useState(false);

  const correctionGroups = groupCorrections(state.corrections);
  const items: React.ReactNode[] = [];

  const totalItems = state.messages.length;
  const shouldFold = totalItems > MAX_VISIBLE && !showAll;
  const startIdx = shouldFold ? totalItems - MAX_VISIBLE : 0;

  if (shouldFold) {
    items.push(
      React.createElement(
        "div",
        { key: "earlier", "data-testid": "earlier-marker" },
        React.createElement(
          "button",
          {
            "data-testid": "show-earlier-btn",
            onClick: () => setShowAll(true),
          },
          `Show earlier messages (${totalItems - MAX_VISIBLE})`
        )
      )
    );
  }

  for (let i = startIdx; i < state.messages.length; i++) {
    const msg = state.messages[i];
    items.push(renderMessage(msg));
    if (msg.role === "user") {
      const msgCorrections = correctionGroups.get(msg.id) || [];
      const bubble = renderCorrectionBubble(msg.id, msgCorrections);
      if (bubble) items.push(bubble);
    }
  }

  return React.createElement("div", null, ...items);
}
