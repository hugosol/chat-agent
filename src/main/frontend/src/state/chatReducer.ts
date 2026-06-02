import type { ChatState, Action } from "./chatState";
import { initialState } from "./chatState";

export function chatReducer(state: ChatState, action: Action): ChatState {
  switch (action.type) {
    case "SESSION_STARTED":
      return {
        ...initialState,
        appStatus: "UserTurn",
      };
    case "AGENT_STREAM_DELTA": {
      const existingIdx = state.messages.findIndex(
        (m) => m.id === action.messageId && m.streaming
      );
      if (existingIdx !== -1) {
        const messages = [...state.messages];
        messages[existingIdx] = {
          ...messages[existingIdx],
          text: messages[existingIdx].text + action.delta,
        };
        return { ...state, messages };
      }
      return {
        ...state,
        messages: [
          ...state.messages,
          {
            id: action.messageId,
            role: "agent",
            text: action.delta,
            streaming: true,
          },
        ],
        streamInProgress: true,
      };
    }
    case "AGENT_STREAM_END": {
      const idx = state.messages.findIndex(
        (m) => m.id === action.messageId && m.streaming
      );
      if (idx !== -1) {
        const messages = [...state.messages];
        messages[idx] = {
          ...messages[idx],
          text: action.text,
          streaming: false,
        };
        return {
          ...state,
          messages,
          tokenUsage: action.tokenUsage,
          streamInProgress: false,
        };
      }
      return {
        ...state,
        tokenUsage: action.tokenUsage,
        streamInProgress: false,
      };
    }
    case "CORRECTION_RESULT":
      return {
        ...state,
        corrections: [...state.corrections, ...action.corrections],
      };
    case "STATE_UPDATE":
      return {
        ...state,
        tokenUsage: action.tokenUsage,
      };
    case "SESSION_RESUMED": {
      const messages = action.messages.map((m) => ({
        id: m.messageId ?? 0,
        role: (m.role.toUpperCase() === "USER" ? "user" : "agent") as "user" | "agent",
        text: m.content,
        streaming: false,
      }));
      return {
        ...initialState,
        messages,
        corrections: action.corrections,
        tokenUsage: action.tokenUsage,
        appStatus: "UserTurn",
      };
    }
    case "WS_CLOSED":
      return {
        ...initialState,
        appStatus: "Disconnected",
      };
    case "USER_MESSAGE_SENT":
      return {
        ...state,
        messages: [
          ...state.messages,
          {
            id: action.messageId,
            role: "user",
            text: action.text,
            streaming: false,
          },
        ],
      };
    case "SESSION_REPORT":
      return {
        ...initialState,
        appStatus: "Connected",
        report: action.report,
      };
    case "SET_APP_STATUS":
      return {
        ...state,
        appStatus: action.appStatus,
        statusPayload: action.statusPayload ?? null,
      };
    case "DISMISS_REPORT":
      return {
        ...state,
        report: null,
      };
    default:
      return state;
  }
}
