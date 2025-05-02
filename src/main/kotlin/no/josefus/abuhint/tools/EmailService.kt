package no.josefus.abuhint.tools

import com.resend.*
import com.resend.core.exception.ResendException
import com.resend.services.emails.model.CreateEmailOptions
import dev.langchain4j.agent.tool.Tool
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class EmailService (
                     @Value("\${resend.api-key}") private val apiKey: String,
                     @Value("\${resend.from}") private val from: String,
                     @Value("\${resend.subject}") private val subject: String ){

    private val logger: Logger = LoggerFactory.getLogger(EmailService::class.java)
    private val resend = Resend(apiKey)

    /**
     * Sends an email using the Resend API.
     *
     * @param html The HTML content of the email.
     */
    @Tool(name = "sendEmail")
    fun sendEmail(html: String, to: String){
        val params = CreateEmailOptions.builder()
            .from(from)
            .to(to)
            .subject(subject)
            .html(html)
            .build()

        return try {
            val data = resend.emails().send(params)
            logger.info("Email sent successfully with ID: ${data.id}")
        } catch (e: ResendException) {
            print("Failed to send email: ${e.message}")
        }
    }
}