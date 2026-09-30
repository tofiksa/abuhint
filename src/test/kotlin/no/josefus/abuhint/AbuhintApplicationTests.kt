package no.josefus.abuhint

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestPropertySource

@SpringBootTest
@TestPropertySource(
    properties = [
        "spring.jwt.secret=YWh1aGludC1jb250ZXh0LWxvYWQtdGVzdC1qd3Qtc2VjcmV0LWF0LWxlYXN0LTMyLWJ5dGVzISE=",
        "github.repository-url=https://github.com/example/repo",
        "github.repo-token=test-github-token",
        "langchain4j.open-ai.chat-model.api-key=test-openai-key",
        "langchain4j.open-ai.streaming-chat-model.api-key=test-openai-key",
        "langchain4j.gemini.api-key=test-gemini-key",
        "langchain4j.gemini.project-id=test-gcp-project",
        "langchain4j.gemini.location=us-central1",
        "pinecone.api-key=test-pinecone-key",
        "resend.api-key=test-resend-key",
        "resend.from=test@example.com",
        "web-search.enabled=false",
    ],
)
class AbuhintApplicationTests {

    @Test
    fun contextLoads() {
    }
}
