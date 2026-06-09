import { createContext, useContext, useMemo, useState } from "react";

const ChatContext = createContext(null);

export function ChatProvider({ children }) {
  const [mode, setMode] = useState("advisor");
  const [depth, setDepth] = useState("standard");

  const value = useMemo(() => ({ mode, setMode, depth, setDepth }), [mode, depth]);

  return <ChatContext.Provider value={value}>{children}</ChatContext.Provider>;
}

export function useChatSettings() {
  const context = useContext(ChatContext);
  if (!context) {
    throw new Error("useChatSettings must be used inside ChatProvider");
  }
  return context;
}
