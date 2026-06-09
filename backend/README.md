# Backend

Spring Boot API for BUISNESS BOT.

## Run

```bash
cd backend
export GEMINI_API_KEY=your_key_here
mvn -Dmaven.repo.local=../.m2/repository spring-boot:run
```

The API serves:

- `POST /api/chat`
- Static fallback UI from `src/main/resources/static`

## Structure

- `src/main/java`: Spring Boot source code
- `src/main/resources`: configuration and static UI
- `uploads`: future uploaded file storage
- `logs`: runtime logs
- `config/controllers/middleware/models/routes/services/repository/utils`: production-style extension folders
