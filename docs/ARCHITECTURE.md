# Architecture

```text
frontend -> backend REST API -> AI provider
                          -> file extraction pipeline
                          -> database
                          -> vector database
```

The current backend is intentionally preserved as a working Spring Boot app inside `backend/`.

The React frontend scaffold is ready for migration from the static UI when needed.
