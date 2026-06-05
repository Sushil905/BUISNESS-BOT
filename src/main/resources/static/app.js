const messagesEl = document.querySelector("#messages");
const form = document.querySelector("#chatForm");
const input = document.querySelector("#messageInput");
const resetButton = document.querySelector("#resetChat");
const saveReportButton = document.querySelector("#saveReport");
const clearReportsButton = document.querySelector("#clearReports");
const reportsList = document.querySelector("#reportsList");
const quickButtons = document.querySelectorAll("[data-prompt]");

const greeting =
  "Namaste! Main BUISNESS BOT hoon. Aap business idea, market research, competitor analysis, business plan, marketing strategy, startup cost, pitch deck, persona, SWOT, ya pricing ke baare me pooch sakte ho.";

const chatKey = "buisnessBotChat";
const reportsKey = "buisnessBotReports";
const sessionKey = "buisnessBotSession";

let sessionId = localStorage.getItem(sessionKey) || crypto.randomUUID();
let chatHistory = loadJson(chatKey, []);
let savedReports = loadJson(reportsKey, []);
let latestBotReply = "";

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

function addMessage(role, text, shouldPersist = true) {
  const message = document.createElement("article");
  message.className = `message ${role}`;
  message.textContent = text;
  messagesEl.appendChild(message);
  messagesEl.scrollTop = messagesEl.scrollHeight;

  if (role === "bot") {
    latestBotReply = text;
  }

  if (shouldPersist) {
    chatHistory.push({ role, text });
    persistChat();
  }
}

async function askBot(text) {
  const response = await fetch("/api/chat", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify({
      sessionId,
      message: text,
    }),
  });

  if (!response.ok) {
    throw new Error("Chat API request failed");
  }

  return response.json();
}

async function sendMessage(text) {
  const cleanText = text.trim();
  if (!cleanText) return;

  addMessage("user", cleanText);
  input.disabled = true;

  try {
    const data = await askBot(cleanText);
    addMessage("bot", data.reply);
    renderReports();
  } catch (error) {
    addMessage("bot", "Server se connect nahi ho paya. Spring Boot app running hai kya?");
  } finally {
    input.disabled = false;
    input.focus();
  }
}

function resetChat() {
  sessionId = crypto.randomUUID();
  localStorage.setItem(sessionKey, sessionId);
  chatHistory = [];
  latestBotReply = "";
  persistChat();
  messagesEl.innerHTML = "";
  addMessage("bot", greeting);
}

function saveLatestReport() {
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

function restoreChat() {
  messagesEl.innerHTML = "";

  if (chatHistory.length === 0) {
    addMessage("bot", greeting);
    return;
  }

  chatHistory.forEach((entry) => addMessage(entry.role, entry.text, false));
}

form.addEventListener("submit", (event) => {
  event.preventDefault();
  sendMessage(input.value);
  input.value = "";
});

quickButtons.forEach((button) => {
  button.addEventListener("click", () => sendMessage(button.dataset.prompt));
});

resetButton.addEventListener("click", resetChat);
saveReportButton.addEventListener("click", saveLatestReport);
clearReportsButton.addEventListener("click", clearReports);

restoreChat();
renderReports();
