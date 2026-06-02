import React, { useRef } from "react";
import { useChatContext } from "../../state/ChatContext";
import { isSessionActive } from "../../shared/utils";

export function ChatInput(): React.ReactElement {
  const { state, dispatch, send } = useChatContext();
  const inputRef = useRef<HTMLInputElement | null>(null);

  const disabled = !isSessionActive(state.appStatus) || state.streamInProgress;

  function handleSend(): void {
    const input = inputRef.current;
    if (!input) return;
    const text = input.value.trim();
    if (!text) return;
    const nextId = state.messages.filter((m) => m.role === "user").length + 1;
    send({ type: "USER_INPUT", text, messageId: nextId });
    dispatch({ type: "USER_MESSAGE_SENT", messageId: nextId, text });
    input.value = "";
  }

  function onKeyDown(e: React.KeyboardEvent<HTMLInputElement>): void {
    if (e.key === "Enter") handleSend();
  }

  return React.createElement(
    React.Fragment,
    null,
    React.createElement("input", {
      ref: inputRef,
      "data-testid": "text-input",
      type: "text",
      disabled,
      placeholder: state.streamInProgress ? "Agent is typing..." : "Type your message...",
      autoComplete: "off",
      onKeyDown,
    }),
    React.createElement(
      "button",
      {
        key: "send",
        "data-testid": "send-btn",
        className: "btn btn-send",
        disabled,
        onClick: handleSend,
      },
      "Send"
    )
  );
}
