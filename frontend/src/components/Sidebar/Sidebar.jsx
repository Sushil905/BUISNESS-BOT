const prompts = [
  "Business idea generator",
  "Market research assistant",
  "Competitor analysis",
  "Business plan generator",
  "Sales and marketing strategy",
  "Startup cost calculator",
  "Pitch deck content generator",
  "Customer persona builder",
  "SWOT analysis generator",
];

export function Sidebar({ onPrompt }) {
  return (
    <aside className="sidebar">
      <p className="eyebrow">Prompt library</p>
      {prompts.map((prompt, index) => (
        <button key={prompt} type="button" onClick={() => onPrompt(prompt)}>
          <span>{String(index + 1).padStart(2, "0")}</span>
          {prompt}
        </button>
      ))}
    </aside>
  );
}
