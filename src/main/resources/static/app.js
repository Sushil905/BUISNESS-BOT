const messagesEl = document.querySelector("#messages");
const form = document.querySelector("#chatForm");
const input = document.querySelector("#messageInput");
const resetButton = document.querySelector("#resetChat");
const saveReportButton = document.querySelector("#saveReport");
const clearReportsButton = document.querySelector("#clearReports");
const reportsList = document.querySelector("#reportsList");
const suggestionsEl = document.querySelector("#suggestions");
const quickButtons = document.querySelectorAll("[data-prompt]");

const greeting =
  "Namaste! Main BUISNESS BOT hoon. Tum koi bhi question pooch sakte ho. Business, startup, coding, planning, ya general explanation - main best answer aur next steps dene ki koshish karunga.";

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
    { label: "Ask anything", text: "Bhai help karega meri?" },
    { label: "Idea", text: "Business idea generator for my startup" },
    { label: "Market research", text: "Market research assistant for my business" },
    { label: "Competitors", text: "Competitor analysis for my business" },
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
  message.textContent = "BUISNESS BOT is thinking...";
  messagesEl.appendChild(message);
  messagesEl.scrollTop = messagesEl.scrollHeight;
}

function hideTyping() {
  document.querySelector("#typingIndicator")?.remove();
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
  showTyping();

  try {
    const data = await askBot(cleanText);
    hideTyping();
    addMessage("bot", data.reply);
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
  if (latestBotReply) {
    updateSuggestions(latestBotReply);
  }
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
