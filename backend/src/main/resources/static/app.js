const messagesEl = document.querySelector("#messages");
const form = document.querySelector("#chatForm");
const input = document.querySelector("#messageInput");
const appShell = document.querySelector(".chatgpt-shell");
const resetButton = document.querySelector("#resetChat");
const saveReportButton = document.querySelector("#saveReport");
const clearReportsButton = document.querySelector("#clearReports");
const copyLatestButton = document.querySelector("#copyLatest");
const exportChatButton = document.querySelector("#exportChat");
const attachButton = document.querySelector("#attachButton");
const fileInput = document.querySelector("#fileInput");
const attachmentTray = document.querySelector("#attachmentTray");
const reportsList = document.querySelector("#reportsList");
const suggestionsEl = document.querySelector("#suggestions");
const quickButtons = document.querySelectorAll("[data-prompt]");
const modeButtons = document.querySelectorAll("[data-mode]");
const depthButtons = document.querySelectorAll("[data-depth]");
const accountButton = document.querySelector("#accountButton");
const accountLabel = document.querySelector("#accountLabel");
const accountModal = document.querySelector("#accountModal");
const closeAccountModalButton = document.querySelector("#closeAccountModal");
const accountForm = document.querySelector("#accountForm");
const accountNameInput = document.querySelector("#accountNameInput");
const accountEmailInput = document.querySelector("#accountEmailInput");
const accountPasswordInput = document.querySelector("#accountPasswordInput");
const logoutAccountButton = document.querySelector("#logoutAccount");
const uploadToolButton = document.querySelector("#uploadTool");
const voiceToolButton = document.querySelector("#voiceTool");
const webSearchToolButton = document.querySelector("#webSearchTool");
const deepResearchToolButton = document.querySelector("#deepResearchTool");

const greeting =
  "Namaste! Main BUISNESS BOT hoon. Ab tum kisi bhi domain ka question pooch sakte ho - science, coding, career, finance, business, writing, daily life. Main direct answer, reasoning, example, aur next steps dunga.";

const chatKey = "buisnessBotChat";
const reportsKey = "buisnessBotReports";
const sessionKey = "buisnessBotSession";
const accountKey = "buisnessBotAccount";

let sessionId = localStorage.getItem(sessionKey) || crypto.randomUUID();
let chatHistory = loadJson(chatKey, []);
let savedReports = loadJson(reportsKey, []);
let latestBotReply = "";
let responseMode = "advisor";
let responseDepth = "standard";
let selectedAttachments = [];
let currentAccount = loadJson(accountKey, null);

localStorage.setItem(sessionKey, sessionId);

function loadJson(key, fallback) {
  try {
    return JSON.parse(localStorage.getItem(key)) || fallback;
  } catch (error) {
    return fallback;
  }
}

function persistChat() {
  localStorage.setItem(chatKey, JSON.stringify(chatHistory.slice(-80)));
}

function persistReports() {
  localStorage.setItem(reportsKey, JSON.stringify(savedReports));
}

function persistAccount(account) {
  localStorage.setItem(accountKey, JSON.stringify(account));
}

function renderAccount() {
  if (!accountLabel) return;
  accountLabel.textContent = currentAccount?.name || "User";
  logoutAccountButton.hidden = !currentAccount;
  updateChatAccess();
}

function isLoggedIn() {
  return Boolean(currentAccount?.email);
}

function updateChatAccess() {
  const locked = !isLoggedIn();
  appShell.classList.toggle("is-locked", locked);
  input.disabled = locked;
  fileInput.disabled = locked;
  attachButton.classList.toggle("disabled", locked);
  input.placeholder = locked ? "Login to use Business Bot" : "Ask anything";
}

function requireAccount() {
  if (isLoggedIn()) return true;
  addMessage("bot", "Pehle Login/Create Account karo, phir Business Bot chat, uploads, reports aur tools use kar sakte ho.", false);
  openAccountModal();
  return false;
}

function openAccountModal() {
  accountModal.hidden = false;
  accountNameInput.value = currentAccount?.name || "";
  accountEmailInput.value = currentAccount?.email || "";
  accountPasswordInput.value = "";
  accountNameInput.focus();
}

function closeAccountModal() {
  accountModal.hidden = true;
}

function createOrLoginAccount(event) {
  event.preventDefault();
  const name = accountNameInput.value.trim();
  const email = accountEmailInput.value.trim();
  const password = accountPasswordInput.value.trim();

  if (!name || !email || password.length < 6) {
    addMessage("bot", "Account create karne ke liye name, valid email, aur minimum 6 character password required hai.");
    return;
  }

  currentAccount = {
    name,
    email,
    createdAt: currentAccount?.createdAt || new Date().toISOString(),
  };
  persistAccount(currentAccount);
  renderAccount();
  closeAccountModal();
  addMessage("bot", `Welcome ${name}! Tumhara local Business Bot account ready hai.`);
  input.focus();
}

function logoutAccount() {
  localStorage.removeItem(accountKey);
  currentAccount = null;
  renderAccount();
  closeAccountModal();
  addMessage("bot", "Logged out. Tum dobara Login se account create/sign in kar sakte ho.");
}

function openUploadPicker() {
  if (!requireAccount()) return;
  fileInput.click();
}

function startVoiceInput() {
  if (!requireAccount()) return;

  const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition;
  if (!SpeechRecognition) {
    addMessage("bot", "Voice input is browser dependent. Chrome me microphone speech recognition usually supported hota hai.");
    return;
  }

  const recognition = new SpeechRecognition();
  recognition.lang = "en-IN";
  recognition.interimResults = false;
  recognition.maxAlternatives = 1;
  voiceToolButton.classList.add("listening");
  recognition.start();

  recognition.addEventListener("result", (event) => {
    const transcript = event.results[0][0].transcript;
    input.value = transcript;
    input.focus();
  });

  recognition.addEventListener("end", () => {
    voiceToolButton.classList.remove("listening");
  });

  recognition.addEventListener("error", () => {
    voiceToolButton.classList.remove("listening");
    addMessage("bot", "Voice capture nahi ho paya. Microphone permission aur browser support check karo.");
  });
}

function prepareWebSearch() {
  if (!requireAccount()) return;
  input.value = "Web Search: ";
  input.focus();
  addMessage("bot", "Web Search mode ready. Topic type karo; main current-web style answer structure bana dunga. Real live browsing ke liye backend search API connect karna padega.", false);
}

function prepareDeepResearch() {
  if (!requireAccount()) return;
  input.value = "Deep Research: ";
  input.focus();
  addMessage("bot", "Deep Research mode ready. Topic type karo; main sources, assumptions, comparison, and action steps ke saath detailed research format banaunga.", false);
}

function setSuggestions(prompts) {
  suggestionsEl.innerHTML = "";

  prompts.forEach((prompt) => {
    const button = document.createElement("button");
    button.type = "button";
    button.textContent = prompt.label;
    button.addEventListener("click", () => sendMessage(prompt.text));
    suggestionsEl.appendChild(button);
  });
}

function updateSuggestions(botText) {
  const text = botText.toLowerCase();

  if (text.includes("choose one") || text.includes("quick start") || text.includes("examples")) {
    setSuggestions([
      { label: "Help me", text: "Bhai help karega meri?" },
      { label: "Business plan", text: "Mere business ke liye plan bana do" },
      { label: "Customers issue", text: "Mere shop me customers nahi aa rahe" },
      { label: "Pricing help", text: "Pricing confusion hai, best price kaise set karu?" },
      { label: "Startup cost", text: "Startup cost calculator budget 50000" },
    ]);
    return;
  }

  if (text.includes("reply with your business type") || text.includes("context do")) {
    setSuggestions([
      { label: "Retail shop", text: "Mera retail shop hai, customers kam aa rahe hain, budget 25000 hai" },
      { label: "Clinic", text: "Meri clinic hai, online booking aur leads chahiye, budget 50000 hai" },
      { label: "Restaurant", text: "Mera restaurant hai, competitors strong hain, marketing plan chahiye" },
    ]);
    return;
  }

  if (text.includes("saved")) {
    setSuggestions([
      { label: "Generate SWOT", text: "SWOT analysis generator" },
      { label: "Make pitch deck", text: "Pitch deck content generator" },
    ]);
    return;
  }

  setSuggestions([
    { label: "Science", text: "What is photosynthesis in simple words?" },
    { label: "Tech", text: "What is API?" },
    { label: "Career", text: "How can I improve my resume?" },
    { label: "Finance", text: "Explain inflation in simple words" },
  ]);
}

function addMessage(role, text, shouldPersist = true) {
  const message = document.createElement("article");
  message.className = `message ${role}`;
  message.textContent = text;
  messagesEl.appendChild(message);
  messagesEl.scrollTop = messagesEl.scrollHeight;

  if (role === "bot") {
    latestBotReply = text;
    updateSuggestions(text);
  }

  if (shouldPersist) {
    chatHistory.push({ role, text });
    persistChat();
  }
}

function showTyping() {
  suggestionsEl.innerHTML = "";
  const message = document.createElement("article");
  message.className = "message bot typing";
  message.id = "typingIndicator";
  message.textContent = "BUISNESS BOT is reading your question...";
  messagesEl.appendChild(message);
  messagesEl.scrollTop = messagesEl.scrollHeight;
}

function hideTyping() {
  document.querySelector("#typingIndicator")?.remove();
}

function formatFileSize(size) {
  if (!size) return "Unknown size";
  if (size < 1024) return `${size} B`;
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`;
  return `${(size / (1024 * 1024)).toFixed(1)} MB`;
}

function getFileCategory(file) {
  const name = file.name.toLowerCase();

  if (file.type.startsWith("image/")) return "image";
  if (file.type.startsWith("video/")) return "video";
  if (file.type === "application/pdf" || name.endsWith(".pdf")) return "pdf";
  if (name.endsWith(".ppt") || name.endsWith(".pptx")) return "presentation";
  return "file";
}

function getFileLabel(category) {
  const labels = {
    image: "IMG",
    video: "VID",
    pdf: "PDF",
    presentation: "PPT",
    file: "FILE",
  };
  return labels[category] || "FILE";
}

function readTextSnippet(file) {
  if (!file.type.startsWith("text/")) {
    return Promise.resolve("");
  }

  return file.slice(0, 1200).text().catch(() => "");
}

async function addFiles(files) {
  if (!requireAccount()) return;

  const allowedFiles = Array.from(files).filter((file) => {
    const category = getFileCategory(file);
    return ["image", "video", "pdf", "presentation"].includes(category);
  });

  if (allowedFiles.length === 0) {
    addMessage("bot", "PPT, PDF, image, screenshot, ya video file upload karo.");
    return;
  }

  const availableSlots = Math.max(0, 8 - selectedAttachments.length);
  const filesToAdd = allowedFiles.slice(0, availableSlots);

  if (filesToAdd.length < allowedFiles.length) {
    addMessage("bot", "Ek message me maximum 8 attachments add kar sakte ho.");
  }

  const attachments = await Promise.all(filesToAdd.map(async (file) => {
    const category = getFileCategory(file);
    const canPreview = category === "image" || category === "video";

    return {
      id: crypto.randomUUID(),
      file,
      name: file.name,
      type: file.type || "application/octet-stream",
      size: file.size,
      category,
      previewUrl: canPreview ? URL.createObjectURL(file) : "",
      textSnippet: await readTextSnippet(file),
    };
  }));

  selectedAttachments = [...selectedAttachments, ...attachments];
  renderAttachments();
}

function removeAttachment(id) {
  const attachment = selectedAttachments.find((item) => item.id === id);
  if (attachment?.previewUrl) {
    URL.revokeObjectURL(attachment.previewUrl);
  }

  selectedAttachments = selectedAttachments.filter((item) => item.id !== id);
  renderAttachments();
}

function clearAttachments() {
  selectedAttachments.forEach((attachment) => {
    if (attachment.previewUrl) {
      URL.revokeObjectURL(attachment.previewUrl);
    }
  });
  selectedAttachments = [];
  renderAttachments();
  fileInput.value = "";
}

function renderAttachments() {
  attachmentTray.innerHTML = "";
  attachmentTray.classList.toggle("has-files", selectedAttachments.length > 0);

  selectedAttachments.forEach((attachment) => {
    const card = document.createElement("article");
    card.className = "attachment-card";

    const preview = document.createElement("div");
    preview.className = "attachment-preview";

    if (attachment.category === "image") {
      const image = document.createElement("img");
      image.src = attachment.previewUrl;
      image.alt = "";
      preview.appendChild(image);
    } else if (attachment.category === "video") {
      const video = document.createElement("video");
      video.src = attachment.previewUrl;
      video.muted = true;
      video.playsInline = true;
      preview.appendChild(video);
    } else {
      preview.textContent = getFileLabel(attachment.category);
    }

    const meta = document.createElement("div");
    meta.className = "attachment-meta";

    const name = document.createElement("strong");
    name.textContent = attachment.name;

    const detail = document.createElement("span");
    detail.textContent = `${getFileLabel(attachment.category)} · ${formatFileSize(attachment.size)}`;

    meta.append(name, detail);

    const removeButton = document.createElement("button");
    removeButton.className = "attachment-remove";
    removeButton.type = "button";
    removeButton.setAttribute("aria-label", `Remove ${attachment.name}`);
    removeButton.textContent = "x";
    removeButton.addEventListener("click", () => removeAttachment(attachment.id));

    card.append(preview, meta, removeButton);
    attachmentTray.appendChild(card);
  });
}

function attachmentSummary() {
  if (selectedAttachments.length === 0) return "";

  const fileList = selectedAttachments
    .map((attachment) => `- ${attachment.name} (${getFileLabel(attachment.category)}, ${formatFileSize(attachment.size)})`)
    .join("\n");
  return `\n\nAttached files:\n${fileList}`;
}

function attachmentPayload() {
  return selectedAttachments.map((attachment) => ({
    name: attachment.name,
    type: attachment.type,
    size: attachment.size,
    category: attachment.category,
    textSnippet: attachment.textSnippet,
  }));
}

async function askBot(text, attachments = []) {
  const response = await fetch("/api/chat", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify({
      sessionId,
      message: text,
      responseMode,
      responseDepth,
      attachments,
    }),
  });

  if (!response.ok) {
    throw new Error("Chat API request failed");
  }

  return response.json();
}

async function sendMessage(text) {
  if (!requireAccount()) return;

  const cleanText = text.trim();
  const hasAttachments = selectedAttachments.length > 0;
  if (!cleanText && !hasAttachments) return;

  const prompt = cleanText || "Analyze these uploaded files and suggest what I should create from them.";
  const attachments = attachmentPayload();
  addMessage("user", prompt + attachmentSummary());
  input.disabled = true;
  showTyping();

  try {
    const data = await askBot(prompt, attachments);
    hideTyping();
    addMessage("bot", data.reply);
    clearAttachments();
    renderReports();
  } catch (error) {
    hideTyping();
    addMessage("bot", "Server se connect nahi ho paya. Spring Boot app running hai kya?");
  } finally {
    input.disabled = false;
    input.focus();
  }
}

function resetChat() {
  if (!requireAccount()) return;

  sessionId = crypto.randomUUID();
  localStorage.setItem(sessionKey, sessionId);
  chatHistory = [];
  latestBotReply = "";
  persistChat();
  messagesEl.innerHTML = "";
  clearAttachments();
  addMessage("bot", greeting);
}

function saveLatestReport() {
  if (!requireAccount()) return;

  if (!latestBotReply || latestBotReply === greeting) {
    addMessage("bot", "Pehle koi report generate karo, phir save button dabao.");
    return;
  }

  const title = latestBotReply.split("\n").find(Boolean) || "Saved report";
  savedReports.unshift({
    id: crypto.randomUUID(),
    title,
    text: latestBotReply,
    savedAt: new Date().toLocaleString(),
  });
  savedReports = savedReports.slice(0, 12);
  persistReports();
  renderReports();
  addMessage("bot", `Report saved: ${title}`);
}

function renderReports() {
  reportsList.innerHTML = "";

  if (savedReports.length === 0) {
    const empty = document.createElement("p");
    empty.className = "empty-state";
    empty.textContent = "No saved reports yet.";
    reportsList.appendChild(empty);
    return;
  }

  savedReports.forEach((report) => {
    const button = document.createElement("button");
    button.type = "button";
    button.className = "report-item";
    button.innerHTML = `<strong>${report.title}</strong><span>${report.savedAt}</span>`;
    button.addEventListener("click", () => addMessage("bot", report.text));
    reportsList.appendChild(button);
  });
}

function clearReports() {
  savedReports = [];
  persistReports();
  renderReports();
}

function setActiveButton(buttons, activeButton) {
  buttons.forEach((button) => {
    button.classList.toggle("active", button === activeButton);
  });
}

async function copyLatestReply() {
  if (!requireAccount()) return;

  if (!latestBotReply || latestBotReply === greeting) {
    addMessage("bot", "Copy karne ke liye pehle koi answer generate karo.");
    return;
  }

  try {
    await navigator.clipboard.writeText(latestBotReply);
    addMessage("bot", "Latest answer copied.");
  } catch (error) {
    addMessage("bot", latestBotReply);
  }
}

function exportChat() {
  if (!requireAccount()) return;

  if (chatHistory.length === 0) {
    addMessage("bot", "Export karne ke liye chat me kuch messages hone chahiye.");
    return;
  }

  const transcript = chatHistory
    .map((entry) => `${entry.role.toUpperCase()}\n${entry.text}`)
    .join("\n\n---\n\n");
  const blob = new Blob([transcript], { type: "text/plain" });
  const link = document.createElement("a");
  link.href = URL.createObjectURL(blob);
  link.download = `buisness-bot-chat-${new Date().toISOString().slice(0, 10)}.txt`;
  link.click();
  URL.revokeObjectURL(link.href);
  addMessage("bot", "Chat transcript exported.");
}

function restoreChat() {
  messagesEl.innerHTML = "";

  if (!isLoggedIn()) {
    addMessage("bot", "Welcome to Business Bot. Login ya account create karo to chat, uploads, saved reports aur AI tools unlock ho jayenge.", false);
    updateSuggestions("login required");
    return;
  }

  if (chatHistory.length === 0) {
    addMessage("bot", greeting);
    return;
  }

  chatHistory.forEach((entry) => addMessage(entry.role, entry.text, false));
  if (latestBotReply) {
    updateSuggestions(latestBotReply);
  }
}

form.addEventListener("submit", (event) => {
  event.preventDefault();
  sendMessage(input.value);
  input.value = "";
});

fileInput.addEventListener("change", () => addFiles(fileInput.files));

["dragenter", "dragover"].forEach((eventName) => {
  form.addEventListener(eventName, (event) => {
    event.preventDefault();
    form.classList.add("drag-over");
  });
});

["dragleave", "drop"].forEach((eventName) => {
  form.addEventListener(eventName, (event) => {
    event.preventDefault();
    form.classList.remove("drag-over");
  });
});

form.addEventListener("drop", (event) => {
  addFiles(event.dataTransfer.files);
});

quickButtons.forEach((button) => {
  button.addEventListener("click", () => sendMessage(button.dataset.prompt));
});

modeButtons.forEach((button) => {
  button.addEventListener("click", () => {
    responseMode = button.dataset.mode;
    setActiveButton(modeButtons, button);
  });
});

depthButtons.forEach((button) => {
  button.addEventListener("click", () => {
    responseDepth = button.dataset.depth;
    setActiveButton(depthButtons, button);
  });
});

resetButton.addEventListener("click", resetChat);
saveReportButton.addEventListener("click", saveLatestReport);
clearReportsButton.addEventListener("click", clearReports);
copyLatestButton.addEventListener("click", copyLatestReply);
exportChatButton.addEventListener("click", exportChat);
accountButton.addEventListener("click", openAccountModal);
closeAccountModalButton.addEventListener("click", closeAccountModal);
accountForm.addEventListener("submit", createOrLoginAccount);
logoutAccountButton.addEventListener("click", logoutAccount);
uploadToolButton.addEventListener("click", openUploadPicker);
voiceToolButton.addEventListener("click", startVoiceInput);
webSearchToolButton.addEventListener("click", prepareWebSearch);
deepResearchToolButton.addEventListener("click", prepareDeepResearch);
accountModal.addEventListener("click", (event) => {
  if (event.target === accountModal) {
    closeAccountModal();
  }
});

restoreChat();
renderReports();
renderAccount();
