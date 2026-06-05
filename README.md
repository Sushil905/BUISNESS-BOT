# BUISNESS BOT

BUISNESS BOT AI is a Spring Boot business assistant that helps users generate
business ideas, analyze markets, create business plans, evaluate competitors,
and develop growth strategies through an intelligent conversational interface.
The frontend is served from Spring Boot static resources and talks to a Java
REST API.

## Run

Install Maven, set your OpenAI API key, then run:

```bash
export OPENAI_API_KEY=your_api_key_here
mvn spring-boot:run
```

Optional model override:

```bash
export OPENAI_MODEL=gpt-5.5
```

If `OPENAI_API_KEY` is not set, the app still runs with local rule-based
fallback answers.

Open:

```text
http://localhost:8080
```

Chat API:

```http
POST /api/chat
Content-Type: application/json

{
  "sessionId": "demo-session",
  "message": "Meri clinic ke liye website aur chatbot chahiye, budget 25000"
}
```

## Current Features

- Business idea generator
- Market research assistant
- Competitor analysis
- Business plan generator
- Sales and marketing strategy
- Startup cost calculator
- Pitch deck content generator
- Customer persona builder
- SWOT analysis generator
- Chat history and saved reports through browser local storage
- OpenAI-powered intelligent answers when `OPENAI_API_KEY` is configured
- Spring Boot REST endpoint
- Java service for chatbot replies
- Business service matching
- Simple package and quote recommendations
- Lead detail extraction for service, business type, budget, email, and phone
- Responsive layout for mobile and desktop

## Next Ideas

- Connect the chat to an AI API
- Save leads to MySQL, PostgreSQL, or Google Sheet
- Add admin settings for services and package prices
- Send booking notifications by email or WhatsApp
