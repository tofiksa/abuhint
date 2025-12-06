# Chatbot Conversation Pipeline (Inventory)

- **Endpoints**
  - `/api/chat/send` and `/api/coach/chat` → `ChatService.processChat` (OpenAI model via `LangChain4jAssistant`).
  - `/api/tech-advisor/chat` → `ChatService.processGeminiChat` (Gemini model via `TechAdvisorAssistant`).
  - `/v1/chat/completions` → `OpenAiCompatibleServiceImpl` → `ChatService.processChat` (uses random chatId per request).

- **Personas & Tools**
  - `LangChain4jAssistant`: Norwegian coach persona with backstory; tools `emailService` and `powerPointTool`; chat memory provider enabled.
  - `TechAdvisorAssistant`: developer persona, Gemini model, chat memory provider enabled.

- **Context Retrieval (pre-call)**
  - `ConcretePineconeChatMemoryStore.retrieveFromPineconeWithTokenLimit` searches Pinecone index `paaskeeggjakt`, namespace = `chatId` (or `startup` if empty), top 50, minScore 0.3.
  - Formats matches as “Previous relevant conversation context:” text block, then appends `User: <message>`.
  - Token cap: 8,192 (approx char/4 tokenizer); trims oldest included matches when exceeded.

- **Memory Store (LLM chat memory)**
  - `LangChain4jConfiguration.chatMemoryProvider` builds `MessageWindowChatMemory` (max 100 messages) backed by `PineconeChatMemoryStore`.
  - `updateMessages` stores each message as `TYPE: content` embedding; caches in-memory; attempts best-effort reload via reflective search.
  - Empty `chatId` falls back to shared namespace `startup` (risk of cross-talk).

- **Other notes**
  - ScoreService call in `ChatController` fetches a game ID but its result is unused.
  - No summarization or distillation of long histories beyond window/token trimming.

