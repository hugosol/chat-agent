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
import { speakText } from "../shared/tts";
import { debugLog } from "../shared/debugLog";
import type { CorrectionData } from "../shared/types";

interface ChatContextValue {
  state: ChatState;
  dispatch: Dispatch<Action>;
  send: (msg: unknown) => void;
}

type ServerMessage = { type: string; [key: string]: unknown };

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
        mode: (msg.mode as string) ?? "",
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
  const sessionIdRef = useRef<string | null>(null);
  const resumePendingRef = useRef(false);
  const modeRef = useRef<string>("");

  const send = useCallback((msg: unknown): void => {
    if (wsRef.current?.readyState === WebSocket.OPEN) {
      wsRef.current.send(JSON.stringify(msg));
    }
  }, []);

  useEffect(() => {
    const ws = new WebSocket(getWsUrl());
    wsRef.current = ws;

    ws.onopen = () => {
      debugLog("WS: connected");
      dispatch({ type: "SET_APP_STATUS", appStatus: "Connected" });
      const savedSessionId =
        typeof localStorage !== "undefined"
          ? localStorage.getItem("sessionId")
          : null;
      if (savedSessionId) {
        sessionIdRef.current = savedSessionId;
        resumePendingRef.current = true;
        ws.send(
          JSON.stringify({ type: "RESUME_SESSION", sessionId: savedSessionId })
        );
      }
    };

    ws.onmessage = (event: MessageEvent) => {
      try {
        const msg = JSON.parse(event.data as string) as ServerMessage;
        debugLog(`WS ← ${msg.type}`);
        const action = toAction(msg);
        if (action) {
          dispatch(action);
        }
        if (msg.type === "SESSION_STARTED" && msg.sessionId) {
          sessionIdRef.current = msg.sessionId as string;
          resumePendingRef.current = false;
          modeRef.current = (msg.mode as string) ?? "";
          if (typeof localStorage !== "undefined") {
            localStorage.setItem("sessionId", msg.sessionId as string);
          }
        }
        if (msg.type === "SESSION_RESUMED") {
          resumePendingRef.current = false;
          modeRef.current = (msg.mode as string) ?? "";
        }
        if (msg.type === "SESSION_REPORT") {
          sessionIdRef.current = null;
          if (typeof localStorage !== "undefined") {
            localStorage.removeItem("sessionId");
          }
        }
        if (msg.type === "TOKEN_WARNING") {
          dispatch({
            type: "SET_APP_STATUS",
            appStatus: "Warning",
            statusPayload: msg.message as string,
          });
        }
        if (msg.type === "ERROR") {
          const errMsg = msg.message as string;
          if (errMsg.toLowerCase().includes("session not found")
              && resumePendingRef.current
              && wsRef.current?.readyState === WebSocket.OPEN) {
            localStorage.removeItem("sessionId");
            sessionIdRef.current = null;
            resumePendingRef.current = false;
            dispatch({ type: "SET_APP_STATUS", appStatus: "Connected" });
          } else {
            dispatch({
              type: "SET_APP_STATUS",
              appStatus: "Error",
              statusPayload: errMsg,
            });
          }
        }
        if (msg.type === "STATE_UPDATE") {
          const serverState = msg.state as string;
          if (serverState === "PROCESSING") {
            dispatch({ type: "SET_APP_STATUS", appStatus: "Processing" });
          } else if (serverState === "SPEAKING") {
            dispatch({ type: "SET_APP_STATUS", appStatus: "UserTurn" });
          }
        }
        if (msg.type === "AGENT_STREAM_END" && msg.text && document.visibilityState === "visible") {
          speakText(msg.text as string, modeRef.current);
        }
      } catch {
        // ignore parse errors
      }
    };

    ws.onclose = () => {
      debugLog("WS: disconnected");
      resumePendingRef.current = false;
      if (typeof speechSynthesis !== "undefined") {
        speechSynthesis.cancel();
      }
      dispatch({ type: "WS_CLOSED" });
    };

    ws.onerror = () => {
      debugLog("WS: error");
    };

    function onVisibilityChange(): void {
      if (document.visibilityState === "visible" && sessionIdRef.current) {
        debugLog("Resume: tab activated");
        ws.send(
          JSON.stringify({
            type: "RESUME_SESSION",
            sessionId: sessionIdRef.current,
          })
        );
      }
    }
    document.addEventListener("visibilitychange", onVisibilityChange);

    return () => {
      document.removeEventListener("visibilitychange", onVisibilityChange);
      wsRef.current = null;
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

export { ChatProvider, useChatContext };
export { ChatContextObj as ChatContext };
