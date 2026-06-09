export function ChatWindow({ messages }) {
  return (
    <section className="chat-window">
      {messages.map((message) => (
        <article className={`message ${message.role}`} key={message.id}>
          {message.text}
        </article>
      ))}
    </section>
  );
}
