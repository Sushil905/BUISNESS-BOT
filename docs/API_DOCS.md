# API Docs

## POST `/api/chat`

Request:

```json
{
  "sessionId": "demo-session",
  "message": "Analyze these uploaded files",
  "responseMode": "advisor",
  "responseDepth": "standard",
  "attachments": [
    {
      "name": "deck.pptx",
      "type": "application/vnd.openxmlformats-officedocument.presentationml.presentation",
      "size": 123456,
      "category": "presentation",
      "textSnippet": ""
    }
  ]
}
```

Response:

```json
{
  "reply": "AI answer",
  "lead": {
    "businessType": "",
    "service": "",
    "budget": "",
    "contact": ""
  }
}
```
