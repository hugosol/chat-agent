import type { CorrectionData } from "../shared/types";

export interface Message {
  id: number;
  role: "user" | "agent";
  text: string;
  streaming?: boolean;
}

export interface ChatState {
  messages: Message[];
  corrections: CorrectionData[];
  tokenUsage: number;
  connectionStatus: "connecting" | "connected" | "disconnected";
  streamInProgress: boolean;
}

export const initialState: ChatState = {
  messages: [],
  corrections: [],
  tokenUsage: 0,
  connectionStatus: "connecting",
  streamInProgress: false,
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
  | { type: "WS_CLOSED" };
