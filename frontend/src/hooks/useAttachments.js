import { useState } from "react";
import { getFileCategory } from "../utils/files.js";

export function useAttachments() {
  const [attachments, setAttachments] = useState([]);

  function addFiles(files) {
    const nextFiles = Array.from(files)
      .filter((file) => ["image", "video", "pdf", "presentation"].includes(getFileCategory(file)))
      .slice(0, 8)
      .map((file) => ({
        id: crypto.randomUUID(),
        file,
        name: file.name,
        type: file.type || "application/octet-stream",
        size: file.size,
        category: getFileCategory(file),
      }));

    setAttachments((current) => [...current, ...nextFiles].slice(0, 8));
  }

  function removeFile(id) {
    setAttachments((current) => current.filter((file) => file.id !== id));
  }

  function clearFiles() {
    setAttachments([]);
  }

  return { attachments, addFiles, removeFile, clearFiles };
}
