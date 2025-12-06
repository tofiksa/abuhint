# Chatbot Memory & Context Retrieval Review (Task B)

Scope: Evaluate how the chatbot retrieves and injects conversational context for natural, human-like replies. Focused on depth, recency, and fidelity of recalled context.

Findings
- Retrieval entry point: `ConcretePineconeChatMemoryStore.retrieveFromPineconeWithTokenLimit` searches Pinecone index `paaskeeggjakt`, namespace = `chatId` (or shared `startup` when empty). Uses `EmbeddingSearchRequest` with `maxResults=50`, `minScore=0.3`.
- Token budget: hard cap 8,192 tokens (approx via char/4 heuristic). Trimming iterates search results in returned order (likely by score) until budget is hit; no recency bias or deduping.
- Context shape: builds a plain text block “Previous relevant conversation context:” with role-prefixed lines. No inline separators, citations, or metadata to help the model distinguish turns or freshness.
- Memory store: `MessageWindowChatMemory` (max 100 messages) backed by Pinecone. Messages stored as `TYPE: content` embeddings without metadata (timestamp/index), making recency ordering impossible at retrieval time.
- Cache reload path: `PineconeChatMemoryStore.loadRecentMessagesFromPinecone` still tries reflective `search(Embedding, Int, Double)`/`findRelevant`; this likely fails on the current store (only `search(EmbeddingSearchRequest)` exists), so caches may start empty after restart, hurting context depth.
- Namespace risk: empty `chatId` falls back to shared `startup`, causing cross-talk between conversations and contaminating retrieval relevance.
- Topical drift: `minScore=0.3` is permissive; with no deduplication or recency weighting, older but semantically similar content can crowd out recent turns.
- No summarization: older context is not distilled, so long chats rely solely on top-k and budget trimming, risking loss of narrative continuity.
- Model-side context: `OpenAiCompatibleServiceImpl` uses a random chatId per request, so OpenAI-compatible endpoint has no longitudinal memory between calls.

Recommendations (prioritized)
1) Fix reload/search compatibility: update `loadRecentMessagesFromPinecone` to use `EmbeddingSearchRequest` (same as main path) so caches repopulate and cold starts preserve memory.
2) Enforce unique namespaces: require non-empty `chatId`; reject or auto-generate per session instead of defaulting to `startup`.
3) Add recency signal: store message metadata (timestamp/turn index) alongside text and include it in embeddings or metadata filtering; sort retrieval by score then recency before trimming.
4) Improve token accounting: use model-aware tokenizer (e.g., tiktoken for OpenAI, Gemini tokenizer) to avoid under/over-trimming and set a safer budget (e.g., 4k–6k prompt tokens).
5) Deduplicate & diversify: collapse near-duplicate matches and ensure both sides of recent turns are present (always include last N user/assistant messages regardless of similarity).
6) Summarize long histories: periodically summarize older context into a distilled note and pin it ahead of retrieval results to maintain narrative continuity within budget.
7) Tune search params: revisit `maxResults`/`minScore`; consider adaptive thresholds based on chat length and ensure namespace isolation to reduce noise.
8) Context formatting: add explicit turn separators and brief recency hints (e.g., “Most recent”) to make the injected context easier for the model to use naturally.
9) Memory for OpenAI-compatible endpoint: allow client-provided `chatId` (or server-issued session ids) so that endpoint can maintain continuity instead of per-call randomness.

