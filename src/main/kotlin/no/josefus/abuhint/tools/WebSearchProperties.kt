package no.josefus.abuhint.tools

data class WebSearchProperties(
    val provider: String = "tavily",
    val apiKey: String = "",
    val baseUrl: String = "https://api.tavily.com",
    val timeoutMs: Long = 5000,
    val maxResults: Int = 6,
    val locale: String = "nb-NO",
    val searchDepth: String = "basic"
) {
    val searchUrl: String = if (baseUrl.endsWith("/search")) baseUrl else "$baseUrl/search"
}

