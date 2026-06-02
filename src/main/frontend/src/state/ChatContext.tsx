import React, {
  createContext,
  useContext,
  useReducer,
  useCallback,
  useRef,
  useEffect,
  type Dispatch,
  type ReactNode,
} from "react";
import type { ChatState, Action } from "./chatState";
import { initialState } from "./chatState";
import { chatReducer } from "./chatReducer";
import { showToast } from "../shared/Toast";
import type { CorrectionData } from "../shared/types";

interface ChatContextValue {
  state: ChatState;
  dispatch: Dispatch<Action>;
  send: (msg: unknown) => void;
}

type ServerMessage = { type: string; [key: string]: unknown };
type VanillaHandler = (msg: ServerMessage) => void;
const vanillaHandlers: Set<VanillaHandler> = new Set();

const VANILLA_TYPES = new Set([
  "SESSION_REPORT",
  "ERROR",
  "TOKEN_WARNING",
  "STATE_UPDATE",
  "WS_CLOSED",
]);

function toAction(msg: ServerMessage): Action | null {
  switch (msg.type) {
    case "SESSION_STARTED":
      return {
        type: "SESSION_STARTED",
        sessionId: msg.sessionId as string,
        mode: msg.mode as string,
      };
    case "AGENT_STREAM_DELTA":
      return {
        type: "AGENT_STREAM_DELTA",
        messageId: msg.messageId as number,
        delta: msg.delta as string,
      };
    case "AGENT_STREAM_END":
      return {
        type: "AGENT_STREAM_END",
        messageId: msg.messageId as number,
        text: msg.text as string,
        tokenUsage: msg.tokenUsage as number,
      };
    case "CORRECTION_RESULT": {
      const corrections = (msg.corrections || []) as CorrectionData[];
      return {
        type: "CORRECTION_RESULT",
        messageId: msg.messageId as number,
        corrections,
      } as Action;
    }
    case "STATE_UPDATE":
      return {
        type: "STATE_UPDATE",
        state: msg.state as string,
        tokenUsage: msg.tokenUsage as number,
      };
    case "SESSION_RESUMED": {
      const messages = (msg.messages || []) as Array<{ role: string; content: string; messageId?: number }>;
      const corrections = (msg.corrections || []) as CorrectionData[];
      return {
        type: "SESSION_RESUMED",
        messages,
        corrections,
        tokenUsage: msg.tokenUsage as number,
      } as Action;
    }
    case "SESSION_REPORT":
      return {
        type: "SESSION_REPORT",
        report: (msg.report || {}) as Record<string, unknown>,
      };
    case "ERROR":
    case "TOKEN_WARNING":
      return null;
    default:
      return null;
  }
}

function getWsUrl(): string {
  if (typeof location === "undefined") return "";
  return (
    (location.protocol === "https:" ? "wss://" : "ws://") +
    location.host +
    "/ws/chat"
  );
}

const ChatContextObj = createContext<ChatContextValue | null>(null);

function ChatProvider({ children }: { children: ReactNode }): JSX.Element {
  const [state, dispatch] = useReducer(chatReducer, initialState);
  const wsRef = useRef<WebSocket | null>(null);

  const send = useCallback((msg: unknown): void => {
    if (wsRef.current?.readyState === WebSocket.OPEN) {
      wsRef.current.send(JSON.stringify(msg));
    }
  }, []);

  useEffect(() => {
    const ws = new WebSocket(getWsUrl());
    wsRef.current = ws;

       (ns as Record<string, unknown>).send = send;

    ws.onopen = () => {
      dispatch({ type: "STATE_UPDATE", state: "Connected", tokenUsage: 0 });
      const savedSessionId =
        typeof localStorage !== "undefined"
          ? localStorage.getItem("sessionId")
          : null;
      if (savedSessionId) {
        ws.send(
          JSON.stringify({ type: "RESUME_SESSION", sessionId: savedSessionId })
        );
      }
    };

    ws.onmessage = (event: MessageEvent) => {
      try {
        const msg = JSON.parse(event.data as string) as ServerMessage;
        const action = toAction(msg);
        if (action) {
          dispatch(action);
        }
        if (msg.type === "SESSION_STARTED" && msg.sessionId) {
          if (typeof localStorage !== "undefined") {
            localStorage.setItem("sessionId", msg.sessionId as string);
          }
        }
        if (msg.type === "SESSION_REPORT") {
          if (typeof localStorage !== "undefined") {
            localStorage.removeItem("sessionId");
          }
        }
        if (msg.type === "TOKEN_WARNING") {
          showToast(msg.message as string, 5000);
        }
        if (msg.type === "ERROR") {
          showToast("Error: " + (msg.message as string), 5000);
        }
        if (VANILLA_TYPES.has(msg.type)) {
          vanillaHandlers.forEach((fn) => fn(msg));
        }
      } catch {
        // ignore parse errors
      }
    };

    ws.onclose = () => {
      dispatch({ type: "WS_CLOSED" });
      vanillaHandlers.forEach((fn) => fn({ type: "WS_CLOSED" }));
    };

    ws.onerror = () => {
      // handled by onclose
    };

    return () => {
      wsRef.current = null;
      delete (ns as Record<string, unknown>).send;
      ws.close();
    };
  }, []);

  const value: ChatContextValue = { state, dispatch, send };

  return React.createElement(ChatContextObj.Provider, { value }, children);
}

function useChatContext(): ChatContextValue {
  const ctx = useContext(ChatContextObj);
  if (!ctx) {
    throw new Error("useChatContext must be used within ChatProvider");
  }
  return ctx;
}

// Phase 2 compat — vanilla bridge for app.js (only 5 non-React message types)
const ns = (window as unknown as Record<string, unknown>).ChatAgent || {};
(window as unknown as Record<string, unknown>).ChatAgent = ns;
(ns as Record<string, unknown>).registerHandler = (handler: VanillaHandler) => {
  vanillaHandlers.add(handler);
};

export { ChatProvider, useChatContext };
export { ChatContextObj as ChatContext };
