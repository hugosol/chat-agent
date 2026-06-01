import { useEffect, useCallback } from "react";
import type { Dispatch } from "react";
import type { Action } from "../state/chatState";
import { showToast } from "../shared/Toast";

// Phase 2 compat — Phase 3 移除
interface ServerMessage {
  type: string;
  [key: string]: unknown;
}

type VanillaHandler = (msg: ServerMessage) => void;

// Phase 2 compat — Phase 3 移除
const vanillaHandlers: Set<VanillaHandler> = new Set();

let activeWs: WebSocket | null = null;

const WS_URL =
  typeof location !== "undefined"
    ? (location.protocol === "https:" ? "wss://" : "ws://") +
      location.host +
      "/ws/chat"
    : "";

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
      const corrections = msg.corrections as
        | Action["corrections"]
        | undefined;
      return {
        type: "CORRECTION_RESULT",
        messageId: msg.messageId as number,
        corrections: corrections || [],
      } as Action;
    }
    case "STATE_UPDATE":
      return {
        type: "STATE_UPDATE",
        state: msg.state as string,
        tokenUsage: msg.tokenUsage as number,
      };
    case "SESSION_RESUMED": {
      const messages = msg.messages as Action["messages"] | undefined;
      const corrections = msg.corrections as
        | Action["corrections"]
        | undefined;
      return {
        type: "SESSION_RESUMED",
        messages: messages || [],
        corrections: corrections || [],
        tokenUsage: msg.tokenUsage as number,
      } as Action;
    }
    case "SESSION_REPORT":
    case "ERROR":
    case "TOKEN_WARNING":
      return null;
    default:
      return null;
  }
}

export function useChatWebSocket(dispatch: Dispatch<Action>): void {
  const handleMessage = useCallback(
    (msg: ServerMessage) => {
      const action = toAction(msg);
      if (action) {
        dispatch(action);
      }

      if (msg.type === "TOKEN_WARNING") {
        showToast(msg.message as string, 5000);
        return;
      }

      if (msg.type === "ERROR") {
        showToast("Error: " + (msg.message as string), 5000);
      }

      // Phase 2 compat — Phase 3 移除
      vanillaHandlers.forEach((fn) => fn(msg));
    },
    [dispatch]
  );

  useEffect(() => {
    const ws = new WebSocket(WS_URL);
    activeWs = ws;

    ws.onopen = () => {
      dispatch({
        type: "STATE_UPDATE",
        state: "Connected",
        tokenUsage: 0,
      });
      const savedSessionId =
        typeof localStorage !== "undefined"
          ? localStorage.getItem("sessionId")
          : null;
      if (savedSessionId) {
        ws.send(
          JSON.stringify({
            type: "RESUME_SESSION",
            sessionId: savedSessionId,
          })
        );
      }
    };

    ws.onmessage = (event: MessageEvent) => {
      try {
        const msg = JSON.parse(event.data as string) as ServerMessage;
        handleMessage(msg);
      } catch {
        // ignore parse errors
      }
    };

    ws.onclose = () => {
      dispatch({ type: "WS_CLOSED" });
      // Phase 2 compat — Phase 3 移除
      vanillaHandlers.forEach((fn) => fn({ type: "WS_CLOSED" }));
    };

    ws.onerror = () => {
      // handled by onclose
    };

    return () => {
      activeWs = null;
      ws.close();
    };
  }, [dispatch, handleMessage]);
}

// Phase 2 compat — Phase 3 移除
function registerHandler(handler: VanillaHandler): void {
  vanillaHandlers.add(handler);
}

// Phase 2 compat — Phase 3 移除
function send(msg: Record<string, unknown>): void {
  if (activeWs && activeWs.readyState === WebSocket.OPEN) {
    activeWs.send(JSON.stringify(msg));
  }
}

// Phase 2 compat — Phase 3 移除
const ns = (window as Record<string, unknown>).ChatAgent || {};
(window as Record<string, unknown>).ChatAgent = ns;
(ns as Record<string, unknown>).registerHandler = registerHandler;
(ns as Record<string, unknown>).send = send;
