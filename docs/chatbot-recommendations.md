# Recommendations to Improve Naturalness & Context Use (Task E)

Priority tiers

P0 (must-do to fix correctness/noise)
- [x] Enforce chatId per session; reject empty chatId or auto-generate to avoid `startup` cross-talk.
- [x] Update Pinecone reload path to use `EmbeddingSearchRequest`; ensure cache repopulates after restart.
- [x] Add recency metadata (timestamp/turn index) and sort by score then recency before trimming.
- [x] Accept client `chatId` on OpenAI-compatible endpoint to preserve continuity.

P1 (high-impact quality)
- Slim persona prompts; add concise, friendly style cues (acknowledge, brief answer first, one clarifying question if uncertain), and grounding (“don’t invent personal employment history”).
- [x] Tune retrieval: tighten `minScore` adaptively; deduplicate near-identical matches; always include last N turns (e.g., 2–3 user/assistant) before semantic hits.
- [x] Use model-aware token counting (OpenAI/Gemini tokenizer) and set conservative context budget (e.g., 4k–6k prompt tokens).
- [x] Improve context formatting: clearly mark recap section, add recency hints, and keep it concise.
- [x] Add light post-processing: enforce soft length targets; keep lead acknowledgement + direct answer; trim overlong outputs.

P2 (medium-term resilience)
- [x] Summarize older history into a distilled note and pin it in context.
- [x] Add safety rails: avoid fabrications about personal backstories; decline when unsure; avoid unsolicited tool use.
- [x] Tool prompts: ask permission before email/PowerPoint; state if action isn’t possible.

P3 (nice-to-have)
- [x] Add conversation-level telemetry to measure recall rate, response length, and user satisfaction proxies.
- [x] Add structured citations/attributions when recalling prior turns (helps perceived memory).
- Add web-search rollout & failure handling:
  - Feature-flagged (`WEB_SEARCH_ENABLED`) and provider-configurable; require API key via env.
  - Graceful user messaging for timeouts/5xx/429 and empty results.
  - Log tool invocations (provider/query/tookMs) for observability.

Concrete implementation steps
- [x] Change `loadRecentMessagesFromPinecone` to use `EmbeddingSearchRequest` and share the search helper used in main retrieval.
- [x] Add metadata (timestamp/index) when storing messages; sort retrieval results by score then recency; include last N turns before semantic trim.
- [ ] Replace char/4 tokenizer with model tokenizers; adjust `maxContextTokens` to safe prompt budget.
- [x] Require/issue chatId in controllers; allow chatId on `/v1/chat/completions`.
- [ ] Rewrite system prompts for both personas to be shorter, grounded, and polite; add clarifying-question guidance and brevity preference.
- [ ] Add post-processing hook to cap length and prepend brief acknowledgement.
- [ ] Introduce summarization for long histories (e.g., after 50–100 turns) and store a “summary” segment in the namespace.
