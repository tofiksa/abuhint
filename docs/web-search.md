## Web Search Tool

### Environment
- `WEB_SEARCH_ENABLED=true`
- `WEB_SEARCH_PROVIDER=tavily`
- `TAVILY_API_KEY=...`
- `BRAVE_API_KEY=...` (fallback)
- `WEB_SEARCH_TIMEOUT_MS=5000`
- `WEB_SEARCH_MAX_RESULTS=6`
- `WEB_SEARCH_LOCALE=nb-NO`
- `WEB_SEARCH_CACHE_TTL_S=300`
- `WEB_SEARCH_SAFE_MODE=moderate`

### Usage notes
- Use only when the question needs fresh/external info.
- Always cite URLs; do not mix real sources into fiktive anekdoter (Abu-hint/Abdikverrulant).
- Keep results short (title, URL, snippet). If safe mode is on, avoid unsafe content.

### Failure modes & messaging
- No results: “Fant ingen oppdaterte kilder; sier ifra og svarer uten eksterne data.”
- Timeout/API error: “Klarte ikke hente resultater nå (årsak X); svarer uten eksterne data.”
- Rate limit: “Søk er midlertidig begrenset, forsøker senere.”
- Missing key/config: disable tool and surface: “Web-søk ikke konfigurert.”
- Provider fallback: if primary fails, try fallback once; otherwise return graceful message.

### Testing
- Unit/contract tests should mock provider responses and error cases.
- Verify citation format and that answers clearly separate sources from fiktive anekdoter.

