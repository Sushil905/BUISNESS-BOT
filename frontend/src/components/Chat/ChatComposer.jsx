import { AttachmentTray } from "./AttachmentTray.jsx";

export function ChatComposer({ attachments, onAttach, onRemoveAttachment, onSubmit }) {
  return (
    <form className="composer" onSubmit={onSubmit}>
      <input
        id="fileInput"
        type="file"
        accept=".pdf,.ppt,.pptx,image/*,video/*"
        multiple
        onChange={(event) => onAttach(event.target.files)}
      />
      <AttachmentTray attachments={attachments} onRemove={onRemoveAttachment} />
      <div className="composer-row">
        <label className="attach-button" htmlFor="fileInput">Upload</label>
        <input name="message" placeholder="Ask anything or upload files..." />
        <button type="submit">Send</button>
      </div>
    </form>
  );
}
