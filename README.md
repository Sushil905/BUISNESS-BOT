# BUISNESS BOT

BUISNESS BOT AI is a Spring Boot business assistant that helps users generate
business ideas, analyze markets, create business plans, evaluate competitors,
and develop growth strategies through an intelligent conversational interface.
The frontend is served from Spring Boot static resources and talks to a Java
REST API.

## Run

Install Maven, then run:

```bash
mvn spring-boot:run
```

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
