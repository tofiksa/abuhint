package no.josefus.abuhint.service

import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Mono

@Service
class ScoreService(private val webClient: WebClient) {

    constructor() : this(WebClient.builder().build())

    /**
     * Retrieves a game ID from the high score service using the provided access token
     * for authentication.
     *
     * @param accessToken The OAuth access token for authentication
     * @return A Mono containing the game ID as a String
     */
    fun getGameId(accessToken: String?): Mono<String> {
        return webClient.get()
            .uri("https://josefus-highscore-webservice-90b882013762.herokuapp.com/game/id")
            .header("Authorization", "Bearer $accessToken")
            .retrieve()
            .bodyToMono<String>()
            .onErrorResume { e ->
                // Log the error and return a fallback or propagate
                //println("Error retrieving game ID: ${e.message}")
                Mono.error(e)
            }
    }

    /**
     * Fetches the game ID using the provided access token and returns the result.
     *
     * @param accessToken The OAuth access token for authentication
     * @return A Mono containing the game ID as a String
     */
    fun fetchAndReturnGameId(accessToken: String?): Mono<String> {
        val logger = org.slf4j.LoggerFactory.getLogger(ScoreService::class.java)
        //logger.info("Fetching game ID with access token: $accessToken")

        if (accessToken.isNullOrEmpty()) {
            logger.warn("Access token is null or empty")
            return Mono.error(IllegalArgumentException("Access token is required"))
        }
        return getGameId(accessToken)
                .map { gameId ->

                    //logger.info("Retrieved game ID: $gameId")
                    gameId
                }
    }
    
}
