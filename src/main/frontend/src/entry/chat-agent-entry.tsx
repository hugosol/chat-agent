import React, { useState } from "react";
import { createRoot } from "react-dom/client";
import { createPortal } from "react-dom";
import { ChatProvider, useChatContext } from "../state/ChatContext";
import { useChatWebSocket } from "../hooks/useChatWebSocket";
import { Header } from "../components/Header/Header";
import { CorrectionSidebar } from "../components/CorrectionSidebar/CorrectionSidebar";
import type { CorrectionData } from "../shared/types";

const TOKEN_MAX = 128000;

function TokenHeader(): JSX.Element {
  const { state } = useChatContext();
  const pct = Math.min(100, Math.round((state.tokenUsage / TOKEN_MAX) * 100));
  const headerEl = document.querySelector("header");
  if (!headerEl) return React.createElement("div");
  return createPortal(React.createElement(Header, { tokenPercent: pct }), headerEl);
}

function ChatSidebar(): JSX.Element {
  const { state } = useChatContext();
  const [collapsed, setCollapsed] = useState(true);
  const sidebarEl = document.getElementById("correction-sidebar-root");
  if (!sidebarEl) return React.createElement("div");
  return createPortal(
    React.createElement(CorrectionSidebar, {
      corrections: state.corrections as CorrectionData[],
      collapsed,
      onToggle: () => setCollapsed(!collapsed),
    }),
    sidebarEl
  );
}

function WsConnector(): JSX.Element {
  const { dispatch } = useChatContext();
  useChatWebSocket(dispatch);
  return React.createElement("div", { style: { display: "none" } });
}

function App(): JSX.Element {
  return React.createElement(
    ChatProvider,
    null,
    React.createElement(WsConnector),
    React.createElement(TokenHeader),
    React.createElement(ChatSidebar)
  );
}

function mountChatAgent(): void {
  const container = document.createElement("div");
  container.id = "chat-agent-root";
  document.body.appendChild(container);
  createRoot(container).render(React.createElement(App));
}

const ns = (window as Record<string, unknown>).ChatAgent || {};
(window as Record<string, unknown>).ChatAgent = ns;
(ns as Record<string, unknown>).mountChatAgent = mountChatAgent;

mountChatAgent();
