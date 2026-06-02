import React, { useState } from "react";
import { createRoot } from "react-dom/client";
import { ChatProvider, useChatContext } from "../state/ChatContext";
import { Header } from "../components/Header/Header";
import { CorrectionSidebar } from "../components/CorrectionSidebar/CorrectionSidebar";
import { MessageList } from "../components/chat/MessageList";
import { ChatInput } from "../components/chat/ChatInput";
import { Footer } from "../components/chat/Footer";
import { StatusBar } from "../components/chat/StatusBar";
import { ReportModal } from "../components/chat/ReportModal";
import { DebugPanel } from "../components/chat/DebugPanel";
import { FlashcardPanel } from "../components/chat/FlashcardPanel";
import type { CorrectionData } from "../shared/types";
import styles from "./ChatPage.module.css";

type PanelType = "menu" | "correction" | "debug" | "flashcard" | null;

const TOKEN_MAX = 128000;

function App(): JSX.Element {
  const [activePanel, setActivePanel] = useState<PanelType>(null);

  function togglePanel(panel: PanelType): void {
    setActivePanel((prev) => (prev === panel ? null : panel));
  }

  return React.createElement(
    ChatProvider,
    null,
    React.createElement(AppContent, { activePanel, togglePanel })
  );
}

function AppContent({
  activePanel,
  togglePanel,
}: {
  activePanel: PanelType;
  togglePanel: (panel: PanelType) => void;
}): JSX.Element {
  const { state } = useChatContext();
  const pct = Math.min(100, Math.round((state.tokenUsage / TOKEN_MAX) * 100));

  return React.createElement(
    "div",
    { id: "app", className: styles.app },
    React.createElement(Header, {
      tokenPercent: pct,
      activePanel,
      onTogglePanel: togglePanel,
    }),
    React.createElement(
      "main",
      { className: styles.main },
      React.createElement(
        "div",
        { className: styles.mainLayout },
        React.createElement(
          "div",
          { id: "chatArea", className: styles.chatArea },
          React.createElement(MessageList)
        ),
        React.createElement(CorrectionSidebar, {
          corrections: state.corrections as CorrectionData[],
          isOpen: activePanel === "correction",
          onToggle: () => togglePanel("correction"),
        })
      ),
      React.createElement(StatusBar),
      React.createElement(ChatInput)
    ),
    React.createElement(Footer),
    React.createElement(ReportModal),
    React.createElement(DebugPanel, {
      isOpen: activePanel === "debug",
      onToggle: () => togglePanel("debug"),
    }),
    React.createElement(FlashcardPanel, {
      isOpen: activePanel === "flashcard",
      onToggle: () => togglePanel("flashcard"),
    })
  );
}

function mountChatAgent(): void {
  const root = document.getElementById("root");
  if (root) {
    createRoot(root).render(React.createElement(App));
  }
}

const ns = (window as unknown as Record<string, unknown>).ChatAgent || {};
(window as unknown as Record<string, unknown>).ChatAgent = ns;
(ns as Record<string, unknown>).mountChatAgent = mountChatAgent;

mountChatAgent();
