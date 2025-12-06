# Prompt, Persona & Post-processing Review (Task C)

Scope: Assess how prompts/personas and any post-processing affect natural, human-like conversation.

Sources reviewed
- `repository/LangChain4jAssistant.kt` — coach persona, Norwegian system prompt with detailed backstory and tool hints.
- `repository/TechAdvisorAssistantService.kt` — developer persona (Gemini), long expert backstory.
- `service/ChatService.kt` & controllers — no response post-processing; context preamble is plain text.

Findings
- Persona heaviness: Prompts are long, with dated/hyper-specific backstories (Kongsmoen/Hjelmeland regnskap/L’oasis, Abdi-Kverrulant) that can dominate replies and reduce adaptability to user tone/tasks.
- Style guidance: Minimal natural-conversation cues (acknowledge, concise, ask clarifying questions, avoid over-explanation); risk of verbose or lecturing tone.
- Safety/grounding: No explicit guidance to avoid fabrications about employment history; prompts encourage personal anecdotes that are fictional.
- Tooling instructions: Mention email/PowerPoint tools, but no guardrails on when to avoid tools; no instructions to surface inability to act.
- Context header: Injected context is labeled “Previous relevant conversation context” but not framed with turn/recency cues; no instruction to keep summaries brief or to cite/acknowledge remembered details naturally.
- Post-processing: None. Responses are returned raw from the model; no length control, no polite closing/turn-taking hints.
- Endpoint gaps: `OpenAiCompatibleServiceImpl` uses random chatId per request; loses persona continuity and any system-level tone consistency.

Recommendations
1) Slim persona prompts: Keep core role + concise style cues (friendly, concise, acknowledges, asks one clarifying question when uncertain, avoids fabrications, keeps anecdotes minimal).
2) Add safety/grounding: Explicitly avoid making up personal employment details; prefer generic experience framing.
3) Clarify tool usage: Instruct to offer email/PowerPoint only when requested; confirm before triggering; state if not possible.
4) Style/tone guardrails: Ask for missing context before answering; prefer short paragraphs or bullet lists; mirror user brevity; avoid heavy repetition of backstory names.
5) Context formatting hint: Tell the model that preceding “Previous relevant conversation context” is recap; summarize only what’s relevant; keep it brief.
6) Light post-processing: Optionally trim trailing long-winded responses or enforce a soft token/length target; ensure leading acknowledgement plus direct answer.
7) OpenAI-compatible continuity: Accept client `chatId` to retain persona/style across calls, or expose a session id.

