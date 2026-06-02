import type { CorrectionData } from "../shared/types";

export interface Message {
  id: number;
  role: "user" | "agent";
  text: string;
  streaming?: boolean;
}

export type AppStatus =
  | "Connecting"
  | "Connected"
  | "UserTurn"
  | "Processing"
  | "Warning"
  | "Error"
  | "Disconnected";

export interface ChatState {
  appStatus: AppStatus;
  statusPayload: string | null;
  messages: Message[];
  corrections: CorrectionData[];
  tokenUsage: number;
  streamInProgress: boolean;
  report: Record<string, unknown> | null;
}

export const initialState: ChatState = {
  appStatus: "Connecting",
  statusPayload: null,
  messages: [],
  corrections: [],
  tokenUsage: 0,
  streamInProgress: false,
  report: null,
};

export type Action =
  | { type: "SESSION_STARTED"; sessionId: string; mode: string }
  | { type: "AGENT_STREAM_DELTA"; messageId: number; delta: string }
  | { type: "AGENT_STREAM_END"; messageId: number; text: string; tokenUsage: number }
  | { type: "CORRECTION_RESULT"; messageId: number; corrections: CorrectionData[] }
  | { type: "STATE_UPDATE"; state: string; tokenUsage: number }
  | {
      type: "SESSION_RESUMED";
      messages: Array<{ role: string; content: string; messageId?: number }>;
      corrections: CorrectionData[];
      tokenUsage: number;
    }
  | { type: "WS_CLOSED" }
  | { type: "USER_MESSAGE_SENT"; messageId: number; text: string }
  | { type: "SESSION_REPORT"; report: Record<string, unknown> }
  | { type: "SET_APP_STATUS"; appStatus: AppStatus; statusPayload?: string | null }
  | { type: "DISMISS_REPORT" };
