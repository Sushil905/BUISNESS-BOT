# AI Engine

AI orchestration layer for prompts, agents, memory, RAG, embeddings, vector database adapters, and LLM provider routing.

The current runtime uses backend services:

- `GeminiChatService`
- `OpenAiChatService`
- `ChatbotService`

Future extraction can move prompt and RAG logic from Java into this layer.
