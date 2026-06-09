# AI-CHATBOT

Professional AI chatbot monorepo for BUISNESS BOT.

The project now has a production-style structure with a working Spring Boot backend, a React frontend scaffold, an AI engine workspace, database scripts, docs, Docker files, and test folders.

## Structure

```text
AI-CHATBOT/
├── frontend/        React/Vite UI scaffold
├── backend/         Spring Boot API and current working static UI
├── ai-engine/       Prompts, agents, memory, RAG, embeddings, LLM planning
├── database/        Schema, migrations, seeds
├── docs/            API docs, system design, architecture
├── docker/          Dockerfiles and compose file
├── tests/           Frontend, backend, and API test folders
├── .env.example     Environment variable template
├── .gitignore
├── README.md
└── LICENSE
```

## Backend Run

```bash
cd backend
export GEMINI_API_KEY=your_gemini_api_key_here
mvn -Dmaven.repo.local=../.m2/repository spring-boot:run
```

Open:

```text
http://localhost:8080
```

The backend still serves the current polished chatbot UI from:

```text
backend/src/main/resources/static
```

## Frontend Run

The React scaffold is ready for a full SPA migration.

```bash
cd frontend
npm install
npm run dev
```

Frontend dev server:

```text
http://localhost:5173
```

## AI Providers

Primary free-friendly option:

```bash
GEMINI_API_KEY=your_key
GEMINI_MODEL=gemini-2.5-flash-lite
```

Fallback:

```bash
OPENAI_API_KEY=your_key
OPENAI_MODEL=gpt-5.5
```

If no API keys are set, the backend uses local rule-based fallback answers.

## Features

- Professional 3-panel AI workspace UI
- Chat with mode/depth controls
- Gemini-first and OpenAI fallback AI provider support
- Local fallback chatbot logic
- PPT/PDF/image/screenshot/video upload preview
- Attachment-aware chat request payloads
- Saved reports in local browser storage
- Export chat transcript
- AI engine planning folders for prompts, agents, RAG, memory, embeddings, and LLM routing
- Database schema scaffold
- Docker scaffold
- Docs scaffold

## Verification

Backend:

```bash
cd backend
mvn -Dmaven.repo.local=../.m2/repository test
```

## Next Upgrade

For true deep file understanding:

- PDF text extraction
- PPT/PPTX slide extraction
- Image OCR
- Video transcription
- RAG over uploaded files
