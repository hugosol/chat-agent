import React, { useState } from "react";
import { createRoot } from "react-dom/client";
import { createPortal } from "react-dom";
import { ChatProvider, useChatContext } from "../state/ChatContext";
import { Header } from "../components/Header/Header";
import { CorrectionSidebar } from "../components/CorrectionSidebar/CorrectionSidebar";
import { MessageList } from "../components/chat/MessageList";
import { ChatInput } from "../components/chat/ChatInput";
import { Footer } from "../components/chat/Footer";
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

function App(): JSX.Element {
  return React.createElement(
    ChatProvider,
    null,
    React.createElement(MessageList),
    React.createElement(ChatInput),
    React.createElement(Footer),
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

const ns = (window as unknown as Record<string, unknown>).ChatAgent || {};
(window as unknown as Record<string, unknown>).ChatAgent = ns;
(ns as Record<string, unknown>).mountChatAgent = mountChatAgent;

mountChatAgent();
