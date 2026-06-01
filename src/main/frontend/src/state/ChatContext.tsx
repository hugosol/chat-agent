import React, { createContext, useContext, useReducer, type Dispatch, type ReactNode } from "react";
import type { ChatState, Action } from "./chatState";
import { initialState } from "./chatState";
import { chatReducer } from "./chatReducer";

interface ChatContextValue {
  state: ChatState;
  dispatch: Dispatch<Action>;
}

const ChatContextObj = createContext<ChatContextValue | null>(null);

function ChatProvider({ children }: { children: ReactNode }): JSX.Element {
  const [state, dispatch] = useReducer(chatReducer, initialState);
  return React.createElement(ChatContextObj.Provider, { value: { state, dispatch } }, children);
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
