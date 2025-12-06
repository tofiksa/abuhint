# Conversation Simulation Notes (Task D)

Goal: Assess naturalness and context use via simulated prompts (no live runtime available; observations based on code paths and expected model behavior).

Scenarios
1) Context recall (coach persona):
   - Input 1: “Jeg jobber med et lite produktteam og vil forbedre sprint retrospektiver.”
   - Input 2 (same chatId): “Minn meg på hva vi snakket om sist, og foreslå en enkel agenda.”
   - Expected: Retrieves prior turn, acknowledges prior topic (retrospective improvement), proposes brief agenda (e.g., what went well/lessons/experiments), keeps concise tone. Risk: if chatId is empty or memory missing, may respond generically; verbose persona may inject backstory.

2) Clarification behavior:
   - Input: “Kan du hjelpe meg med strategien vår?” (ambiguous)
   - Expected: Asks 1–2 clarifying questions before prescribing. Risk: prompt lacks explicit clarifying guidance; may answer generically without asking.

3) Length/naturalness:
   - Input: “Gi meg et kort svar: hvordan starter jeg et retrospektiv effektivt?”
   - Expected: 2–4 bullet/short sentences; polite acknowledgement. Risk: no post-processing; persona may produce long, story-like responses.

4) Tech advisor recency:
   - Input 1: “Vi deployer et Spring Boot API til GCP Cloud Run, kaldstart er høy.”
   - Input 2: “Hva anbefaler du for å redusere kaldstart?”
   - Expected: Mentions previous Cloud Run context; suggests min instances, JVM tuning, native builds; concise pros/cons. Risk: if namespace missing or memory reload path fails, may omit prior context and answer generically.

5) Cross-talk risk:
   - Input: Use empty chatId with unrelated topics across requests.
   - Expected: Isolation per session; actual: shared `startup` namespace may leak context between users.

Gaps observed (based on code)
- Memory continuity depends on provided chatId; OpenAI-compatible endpoint always randomizes chatId, so no context across calls.
- Reload path can fail (legacy reflective search), so after restart memory cache may start empty.
- No recency ordering/metadata; context injection is score-only; risk of older but semantically similar turns overshadowing the latest.
- No post-processing for brevity; persona prompts lack concise style cues; risk of long, formal responses.

Suggested validations (when runtime available)
- Run the scenarios with fixed chatId; verify returned answers reference prior turn and stay concise.
- Repeat with empty chatId; confirm cross-talk; enforce rejection or auto-generate session ids.
- Check response length and tone; add post-processing or prompt tweaks if verbose.
- Verify Pinecone retrieval after simulated restart (cache cold) once reload path is fixed.

