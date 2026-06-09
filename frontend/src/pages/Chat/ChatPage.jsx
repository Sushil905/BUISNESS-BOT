import { useState } from "react";
import { ChatComposer } from "../../components/Chat/ChatComposer.jsx";
import { ChatWindow } from "../../components/Chat/ChatWindow.jsx";
import { Navbar } from "../../components/Navbar/Navbar.jsx";
import { Sidebar } from "../../components/Sidebar/Sidebar.jsx";
import { useAttachments } from "../../hooks/useAttachments.js";
import { sendChatMessage } from "../../services/chatService.js";
import { useChatSettings } from "../../context/ChatContext.jsx";

export function ChatPage() {
  const [messages, setMessages] = useState([]);
  const { attachments, addFiles, removeFile, clearFiles } = useAttachments();
  const { mode, depth } = useChatSettings();

  async function handleSubmit(event) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const message = String(form.get("message") || "").trim();
    if (!message && attachments.length === 0) return;

    const userText = message || "Analyze these uploaded files.";
    setMessages((current) => [...current, { id: crypto.randomUUID(), role: "user", text: userText }]);

    const data = await sendChatMessage({
      sessionId: crypto.randomUUID(),
      message: userText,
      responseMode: mode,
      responseDepth: depth,
      attachments: attachments.map(({ name, type, size, category }) => ({ name, type, size, category })),
    });

    setMessages((current) => [...current, { id: crypto.randomUUID(), role: "bot", text: data.reply }]);
    clearFiles();
    event.currentTarget.reset();
  }

  return (
    <main className="app">
      <Sidebar onPrompt={(prompt) => setMessages((current) => [...current, { id: crypto.randomUUID(), role: "user", text: prompt }])} />
      <section className="chat-shell">
        <Navbar />
        <ChatWindow messages={messages} />
        <ChatComposer
          attachments={attachments}
          onAttach={addFiles}
          onRemoveAttachment={removeFile}
          onSubmit={handleSubmit}
        />
      </section>
    </main>
  );
}
