package no.josefus.abuhint.service

import dev.langchain4j.data.message.AiMessage
import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.model.ModelProvider
import dev.langchain4j.model.chat.listener.ChatModelRequestContext
import dev.langchain4j.model.chat.listener.ChatModelResponseContext
import dev.langchain4j.model.chat.request.ChatRequest
import dev.langchain4j.model.chat.response.ChatResponse
import dev.langchain4j.model.output.TokenUsage
import no.josefus.abuhint.configuration.OpenAiTokenUsageListener
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Import
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.test.context.TestPropertySource
import kotlin.test.assertEquals

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TokenUsagePersistenceTest.Config::class)
@TestPropertySource(
    properties = [
        "spring.datasource.url=jdbc:h2:mem:token_usage_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;INIT=CREATE DOMAIN IF NOT EXISTS TIMESTAMPTZ AS TIMESTAMP WITH TIME ZONE",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
    ],
)
class TokenUsagePersistenceTest {

    @Autowired
    lateinit var repository: TokenUsageAggregateRepository

    private fun store() = TokenUsageStore(repository)

    @Test
    fun `record accumulates usage for same user chat assistant platform and model`() {
        val context = TokenUsageContext(
            userId = "user-1",
            chatId = "chat-1",
            assistant = "ABUHINT",
            clientPlatform = "android",
        )

        store().record(context, TokenUsageEntry(inputTokens = 10, cachedInputTokens = 2, outputTokens = 5, modelName = "gpt-5.4-mini"))
        store().record(context, TokenUsageEntry(inputTokens = 4, cachedInputTokens = 1, outputTokens = 6, modelName = "gpt-5.4-mini"))

        val summary = store().getUsageForUser("user-1")

        assertEquals(14, summary.inputTokens)
        assertEquals(3, summary.cachedInputTokens)
        assertEquals(11, summary.outputTokens)
        assertEquals(25, summary.totalTokens)
        assertEquals(2, summary.requestCount)
        assertEquals(1, summary.breakdown.size)

        val row = summary.breakdown.single()
        assertEquals("chat-1", row.chatId)
        assertEquals("ABUHINT", row.assistant)
        assertEquals("android", row.clientPlatform)
        assertEquals("gpt-5.4-mini", row.modelName)
    }

    @Test
    fun `usage stays isolated by user`() {
        store().record(
            TokenUsageContext("alice", "chat-1", "ABUHINT", "web"),
            TokenUsageEntry(10, 0, 5, "gpt-5.4-mini"),
        )
        store().record(
            TokenUsageContext("bob", "chat-1", "ABUHINT", "web"),
            TokenUsageEntry(100, 0, 50, "gpt-5.4-mini"),
        )

        val alice = store().getUsageForUser("alice")

        assertEquals(15, alice.totalTokens)
        assertEquals(1, alice.requestCount)
        assertEquals("alice", alice.userId)
    }

    @Test
    fun `zero token usage does not create aggregate row`() {
        store().record(
            TokenUsageContext("user-2", "chat-2", "ABUHINT", "ios"),
            TokenUsageEntry(0, 0, 0, "gpt-5.4-mini"),
        )

        assertEquals(0, repository.count())
        assertEquals(0, store().getUsageForUser("user-2").requestCount)
    }

    @Test
    fun `chat usage returns only matching chat for current user`() {
        store().record(
            TokenUsageContext("user-3", "chat-a", "ABUHINT", "web"),
            TokenUsageEntry(10, 0, 5, "gpt-5.4-mini"),
        )
        store().record(
            TokenUsageContext("user-3", "chat-b", "FAMILIE", "web"),
            TokenUsageEntry(20, 0, 10, "gpt-5.4-mini"),
        )

        val chatUsage = store().getUsageForUserChat("user-3", "chat-b")

        assertEquals(30, chatUsage.totalTokens)
        assertEquals(1, chatUsage.breakdown.size)
        assertEquals("FAMILIE", chatUsage.breakdown.single().assistant)
    }

    @Test
    fun `listener persists usage with context copied from request to response`() {
        val context = TokenUsageContext(
            userId = "user-listener",
            chatId = "chat-listener",
            assistant = "ABUHINT",
            clientPlatform = "android",
        )
        val request = ChatRequest.builder()
            .messages(UserMessage.from("hei"))
            .build()
        val attributes = mutableMapOf<Any, Any>()
        val listener = OpenAiTokenUsageListener(store())

        TokenUsageContextHolder.set(context)
        try {
            listener.onRequest(ChatModelRequestContext(request, ModelProvider.OPEN_AI, attributes))
        } finally {
            TokenUsageContextHolder.clear()
        }

        val response = ChatResponse.builder()
            .aiMessage(AiMessage.from("svar"))
            .modelName("gpt-5.4-mini")
            .tokenUsage(TokenUsage(12, 7, 19))
            .build()

        listener.onResponse(ChatModelResponseContext(response, request, ModelProvider.OPEN_AI, attributes))

        val summary = store().getUsageForUser("user-listener")
        assertEquals(19, summary.totalTokens)
        assertEquals(1, summary.requestCount)
        assertEquals("ABUHINT", summary.breakdown.single().assistant)
        assertEquals("android", summary.breakdown.single().clientPlatform)
    }

    @TestConfiguration
    @EnableJpaRepositories(basePackageClasses = [TokenUsageAggregateRepository::class])
    @EntityScan(basePackageClasses = [TokenUsageAggregateEntity::class])
    class Config
}
