import { formatFileSize } from "../../utils/files.js";

export function AttachmentTray({ attachments, onRemove }) {
  if (attachments.length === 0) return null;

  return (
    <div className="attachment-tray">
      {attachments.map((file) => (
        <article className="attachment-card" key={file.id}>
          <strong>{file.name}</strong>
          <span>{file.category} · {formatFileSize(file.size)}</span>
          <button type="button" onClick={() => onRemove(file.id)}>Remove</button>
        </article>
      ))}
    </div>
  );
}
