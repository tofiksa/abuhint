# Backend (API) Improvements

> Chat API optimization plan for `abuhint` Spring Boot backend.
> Prioritized by impact on chat interaction quality and user experience.

---

## Priority 1 — Streaming & Response Time

### 1.1 Implement SSE Streaming for Chat Responses
- **Status**: `[x]` Done — `7de44d5`
- **Impact**: Critical — users currently wait 6-30s staring at a typing indicator
- **Problem**: `LangChain4jAssistant.chatStream()` and `TechAdvisorAssistant.chatStream()` returning `TokenStream` are declared but never called. `OpenAiCompatibleServiceImpl.createStreamingChatCompletion()` returns a bare `SseEmitter()` with no logic. All 3 chat controllers (`ChatController`, `CoachAssistantController`, `TechAdvisorController`) use synchronous `chat()` calls that block until the full LLM response is ready.
- **Files**:
  - `controller/ChatController.kt:78-100` — synchronous POST endpoint
  - `controller/CoachAssistantController.kt` — same pattern
  - `controller/TechAdvisorController.kt` — same pattern
  - `repository/LangChain4jAssistant.kt` — `chatStream()` declared, unused
  - `repository/TechAdvisorAssistantService.kt` — `chatStream()` declared, unused
  - `service/OpenAiCompatibleServiceImpl.kt:48-51` — dummy SseEmitter
- **Solution**:
  1. Add new streaming endpoints (e.g., `POST /api/chat/stream`) using `SseEmitter` or `Flux<ServerSentEvent<String>>`
  2. Wire `chatStream()` from `LangChain4jAssistant` to produce tokens via `TokenStream`
  3. Apply `postProcessReply()` logic to the final aggregated response (or remove it for streaming)
  4. Keep existing synchronous endpoints for backward compatibility

### 1.2 Use the EmbeddingCache (Already Exists, Never Injected)
- **Status**: `[x]` Done — `40026e4`
- **Impact**: High — saves ~200-500ms per repeated/similar query
- **Problem**: `EmbeddingCache` is a `@Component` with a `ConcurrentHashMap<String, Embedding>` and a `getOrCompute()` method, but it is never injected or called anywhere. `PineconeChatMemoryStore.storeMessagesToPinecone()` calls `embeddingModel.embed()` directly at line 255.
- **Files**:
  - `service/EmbeddingCache.kt:1-17` — exists but unused
  - `repository/PineconeChatMemoryStore.kt:255` — direct embed call, bypasses cache
  - `repository/ConcretePineconeChatMemoryStore.kt` — also calls embed directly
- **Solution**:
  1. Inject `EmbeddingCache` into `PineconeChatMemoryStore` and `ConcretePineconeChatMemoryStore`
  2. Replace all `embeddingModel.embed(text).content()` calls with `embeddingCache.getOrCompute(text, embeddingModel)`
  3. Add bounded size limit and TTL eviction to `EmbeddingCache` (see item 3.1)

---

## Priority 2 — Error Handling & Resilience

### 2.1 Add Global Exception Handler (`@ControllerAdvice`)
- **Status**: `[x]` Done — `2491932`
- **Impact**: High — LLM/Pinecone failures currently become raw 500s with stack traces
- **Problem**: No `@ControllerAdvice` or `@ExceptionHandler` exists. If OpenAI returns a 429 (rate limit), Pinecone times out, or any tool throws, the client receives an unstructured error with potentially sensitive details.
- **Files**: No existing file — needs to be created
- **Solution**:
  1. Create `GlobalExceptionHandler.kt` with `@ControllerAdvice`
  2. Handle: `HttpClientErrorException` (LLM API errors), `TimeoutException`, `RestClientException`, generic `Exception`
  3. Return structured JSON error responses: `{ "error": "...", "code": "..." }`
  4. Log full stack traces server-side, return sanitized messages to client

### 2.2 Add Retry Logic on LLM Calls
- **Status**: `[x]` Done — `b60ddeb`
- **Impact**: High — transient 429/503 from OpenAI/Gemini kills the request
- **Problem**: `ChatService.processChat()` and `processGeminiChat()` have no try-catch around the `assistant.chat()` calls. A single transient API error fails the entire request.
- **Files**:
  - `service/ChatService.kt:89` — `assistant.chat()` call, no retry
  - `service/ChatService.kt:185` — `geminiAssistant.chat()` call, no retry
- **Solution**:
  1. Add Spring Retry (`@Retryable`) or manual retry with exponential backoff (2-3 attempts)
  2. Specifically handle 429 (rate limit — respect `Retry-After` header) and 503 (service unavailable)
  3. Wrap in try-catch to return a user-friendly fallback message on final failure
  4. Consider adding `resilience4j` for circuit-breaker pattern on repeated failures

### 2.3 Add Rate Limiting
- **Status**: `[x]` Done — `9b3e1dd`
- **Impact**: Medium — prevents API credit abuse and protects against DoS
- **Problem**: All endpoints are completely open. No authentication, no API key checks, no throttling. Anyone can call `/api/chat/send` unlimited times.
- **Files**: All controller files
- **Solution**:
  1. Add `bucket4j-spring-boot-starter` or a simple in-memory rate limiter
  2. Rate limit per `chatId` or per IP: e.g., 10 messages/minute, 100 messages/hour
  3. Return `429 Too Many Requests` with `Retry-After` header

---

## Priority 3 — Memory & Performance

### 3.1 Add Bounded Cache Eviction
- **Status**: `[x]` Done — `5a2e2c9`
- **Impact**: Medium — prevents memory leak on long-running server
- **Problem**: All in-memory caches use unbounded `ConcurrentHashMap` with no eviction:
  - `PineconeChatMemoryStore.memoryCache` — full message lists per session
  - `PineconeChatMemoryStore.lastStoredCount` — counters per session
  - `PineconeChatMemoryStore.messageCounters` — AtomicLong per session
  - `PineconeChatMemoryStore.summaryStoredUpTo` — counters per session
  - `LangChain4jConfiguration.embeddingStoreCache` — PineconeEmbeddingStore instances
  - `EmbeddingCache.cache` — Embedding objects (if activated)
- **Files**:
  - `repository/PineconeChatMemoryStore.kt:22-30`
  - `service/EmbeddingCache.kt:10`
  - `configuration/LangChain4jConfiguration.kt`
- **Solution**:
  1. Replace `ConcurrentHashMap` with Caffeine cache (`com.github.ben-manes.caffeine`)
  2. Set `maximumSize(1000)` and `expireAfterAccess(2, TimeUnit.HOURS)` for session caches
  3. Set `maximumSize(5000)` and `expireAfterWrite(1, TimeUnit.HOURS)` for embedding cache
  4. Add cache hit/miss metrics for observability

### 3.2 Reduce Pinecone Calls per Request
- **Status**: `[x]` Done — `0706809`
- **Impact**: Medium — each request triggers 3+ Pinecone round-trips
- **Problem**: `ConcretePineconeChatMemoryStore.retrieveFromPineconeWithTokenLimit()` makes:
  1. Semantic search (embed query + search)
  2. Recent-messages search via `loadRecentMatches()` (embeds a generic "conversation chat message user assistant" string — semantically vague)
  3. Summary search
  4. Then after LLM response: message storage (embed + add)
- **Files**:
  - `repository/ConcretePineconeChatMemoryStore.kt` — `retrieveFromPineconeWithTokenLimit()`
  - `repository/PineconeChatMemoryStore.kt:91` — `loadRecentMessagesFromPinecone()` generic query
- **Solution**:
  1. Batch the semantic search and recent-messages search into a single Pinecone request where possible
  2. For `loadRecentMatches()`: instead of embedding a generic string, use metadata-only filtering on `order` field to get the N most recent vectors (Pinecone supports metadata filters)
  3. Cache the pinned summary for a session (it rarely changes) — only re-fetch every N messages

---

## Priority 4 — Code Quality & Maintainability

### 4.1 Deduplicate `processChat()` / `processGeminiChat()`
- **Status**: `[x]` Done — `c7cabcf`
- **Impact**: Medium — reduces ~180 lines to ~100
- **Problem**: `ChatService.processChat()` (line 29-128) and `processGeminiChat()` (line 130-218) are nearly identical (~90 lines each). The only differences are: `assistant` vs `geminiAssistant`, `openAiTokenizer` vs `geminiTokenizer`, and `"openai"` vs `"gemini"` in telemetry labels.
- **Files**: `service/ChatService.kt:29-218`
- **Solution**: Extract a single private method:
  ```kotlin
  private fun processChatInternal(
      chatId: String,
      userMessage: String,
      assistant: Any, // LangChain4jAssistant or TechAdvisorAssistant
      tokenizer: Tokenizer,
      modelLabel: String
  ): String
  ```

### 4.2 Deduplicate `getMessageText()` (DRY Violation)
- **Status**: `[x]` Done — `2602eb6`
- **Impact**: Low — code hygiene
- **Problem**: The exact same reflection-based method for extracting text from `ChatMessage` exists in both `ChatService` (line 221-252) and `PineconeChatMemoryStore` (line 331-362). Both use the same fallback chain: `singleText()` -> `text()` -> `toString()` parsing.
- **Files**:
  - `service/ChatService.kt:221-252`
  - `repository/PineconeChatMemoryStore.kt:331-362`
- **Solution**:
  1. Create a `ChatMessageUtils.kt` utility object with `fun getMessageText(message: ChatMessage): String`
  2. Replace both copies with calls to the utility
  3. Consider using LangChain4j's built-in `text()` method directly (available in recent versions) instead of reflection

### 4.3 Remove Dead Code
- **Status**: `[x]` Done — `d65e2d0`
- **Impact**: Low — code hygiene
- **Problem**: Several pieces of dead/unused code:
  - `ChatController.kt:84` — `scoreService.fetchAndReturnGameId(credentials)` returns a `Mono<String>` that is never subscribed to or awaited. The result is stored in `gameId` but never used.
  - `ChatController.kt:95-98` — Constructs `OpenAiCompatibleChatMessage` as an expression statement with no side effect. The object is created and immediately discarded.
  - `CoachAssistantServiceImpl` — exists but is never called by any controller.
  - `GeminiModelConfiguration.embeddingStoreCache` — declared but never accessed.
  - `H2 database config` in `application.yml` — configured but no `@Entity` classes exist.
- **Files**: Multiple (see above)
- **Solution**: Remove all dead code and unused configuration.

### 4.4 Improve `ensureAcknowledgement()` Behavior
- **Status**: `[x]` Done — `fde7dea`
- **Impact**: Medium — directly affects chat naturalness
- **Problem**: `ChatService.ensureAcknowledgement()` (line 293-300) prepends "Got it. " to every response that doesn't already start with an acknowledgement phrase. This makes the assistant sound repetitive and robotic, especially for responses that don't need an acknowledgement (e.g., questions, greetings, creative content).
- **Files**: `service/ChatService.kt:293-300`
- **Solution**: Either:
  1. Remove `ensureAcknowledgement()` entirely — let the LLM's system prompt handle conversational style
  2. Or move this behavior into the system prompt as a soft instruction: "Begin responses with a brief acknowledgement when appropriate"

### 4.5 Improve `postProcessReply()` Truncation
- **Status**: `[x]` Done — `464f3f9`
- **Impact**: Medium — prevents mid-sentence truncation
- **Problem**: `postProcessReply()` (line 271-291) applies hard character limits (soft: 3200, hard: 3900) and appends "..." when truncating. This can cut content mid-sentence, mid-word, or mid-code-block.
- **Files**: `service/ChatService.kt:271-291`
- **Solution**:
  1. If truncation is necessary, find the nearest sentence boundary (period, newline) before the limit
  2. Consider increasing or removing the limit — the LLM's `maxTokens` parameter is a better control
  3. At minimum, preserve complete markdown structures (don't truncate inside a code block)

---

## Priority 5 — Observability & Operations

### 5.1 Add Health Check Endpoint
- **Status**: `[x]` Done — `819ea16`
- **Impact**: Low — operational hygiene
- **Problem**: No `/health` or `/actuator/health` endpoint for monitoring.
- **Solution**: Enable Spring Boot Actuator health endpoint, include checks for Pinecone connectivity and LLM API availability.

### 5.2 Add Request/Response Logging Middleware
- **Status**: `[x]` Done — `c7be473`
- **Impact**: Low — debugging aid
- **Problem**: No structured request/response logging. Current logging is scattered and ad-hoc.
- **Solution**: Add a `OncePerRequestFilter` that logs: request method, path, chatId, response status, and latency.

### 5.3 Add Conversation History Endpoint
- **Status**: `[x]` Done — `e9f41d9`
- **Impact**: Medium — enables clients to fetch past messages
- **Problem**: No endpoint to retrieve message history for a chatId. The only access to history is the automatic context injection during a new message. If the client loses local storage, all history is lost from the user's perspective.
- **Solution**:
  1. Add `GET /api/chat/{chatId}/history` endpoint
  2. Return messages from the in-memory cache (or Pinecone if cache miss)
  3. Support pagination: `?limit=50&before=<timestamp>`

---

## Summary Table

| # | Improvement | Priority | Impact | Effort | Status | Commit |
|---|---|---|---|---|---|---|
| 1.1 | SSE Streaming | P1 | Critical | Large | Done | `7de44d5` |
| 1.2 | Use EmbeddingCache | P1 | High | Small | Done | `40026e4` |
| 2.1 | Global Exception Handler | P2 | High | Small | Done | `2491932` |
| 2.2 | Retry Logic on LLM Calls | P2 | High | Medium | Done | `b60ddeb` |
| 2.3 | Rate Limiting | P2 | Medium | Medium | Done | `9b3e1dd` |
| 3.1 | Bounded Cache Eviction | P3 | Medium | Medium | Done | `5a2e2c9` |
| 3.2 | Reduce Pinecone Calls | P3 | Medium | Medium | Done | `0706809` |
| 4.1 | Deduplicate processChat | P4 | Medium | Small | Done | `c7cabcf` |
| 4.2 | Deduplicate getMessageText | P4 | Low | Small | Done | `2602eb6` |
| 4.3 | Remove Dead Code | P4 | Low | Small | Done | `d65e2d0` |
| 4.4 | Improve ensureAcknowledgement | P4 | Medium | Small | Done | `fde7dea` |
| 4.5 | Improve postProcessReply | P4 | Medium | Small | Done | `464f3f9` |
| 5.1 | Health Check Endpoint | P5 | Low | Small | Done | `819ea16` |
| 5.2 | Request/Response Logging | P5 | Low | Small | Done | `c7be473` |
| 5.3 | Conversation History Endpoint | P5 | Medium | Medium | Done | `e9f41d9` |
