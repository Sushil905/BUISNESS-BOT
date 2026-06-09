# System Design

## Runtime

- Frontend: React scaffold plus current Spring static UI
- Backend: Java Spring Boot REST API
- AI providers: Gemini first, OpenAI fallback, local fallback
- Storage: planned relational database and upload storage

## Attachment Flow

1. User selects or drops files
2. Frontend previews images/videos and cards for PDFs/PPTs
3. Frontend sends metadata and any readable snippets
4. Backend appends attachment context to the AI prompt
5. AI returns an attachment-aware response

## Future Deep File Reading

- PDF text extraction
- PPT/PPTX slide extraction
- Image OCR
- Video transcription
- Vector search over extracted content
