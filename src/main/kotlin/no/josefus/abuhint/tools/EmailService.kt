package no.josefus.abuhint.tools

import com.resend.*
import com.resend.core.exception.ResendException
import com.resend.services.emails.model.Attachment
import com.resend.services.emails.model.CreateEmailOptions
import dev.langchain4j.agent.tool.Tool
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.Paths
import java.util.Base64

@Component
class EmailService (
                     @Value("\${resend.api-key}") private val apiKey: String,
                     @Value("\${resend.from}") private val from: String,
                     @Value("\${resend.subject}") private val subject: String = "AbuHintsPresentation"){

    private val logger: Logger = LoggerFactory.getLogger(EmailService::class.java)
    private val resend = Resend(apiKey)

    /**
     * Sends an email using the Resend API.
     *
     * @param html The HTML content of the email.
     */
    @Tool(name = "sendEmail", value = ["Send en e-post med valgfri vedlegg via Resend API"])
    fun sendEmail(
        html: String,
        to: String,
        attachmentPath: String? = null,
        attachmentBase64: String? = null,
        attachmentFileName: String? = null,
        attachmentContentType: String? = null,
        confirm: Boolean = false
    ): String {
        if (!confirm) {
            val attachmentNote = when {
                !attachmentPath.isNullOrBlank() -> " with attachment ${Paths.get(attachmentPath).fileName}"
                !attachmentBase64.isNullOrBlank() -> " with a base64 attachment"
                else -> ""
            }
            val contentTypeNote = attachmentContentType?.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: ""
            return "I can send the email to $to$attachmentNote$contentTypeNote. Reply with confirmation to proceed."
        }
        if (apiKey.isBlank() || from.isBlank()) {
            return "Email sending is not configured. Please set resend API credentials."
        }
        if (!attachmentPath.isNullOrBlank()) {
            val path = Paths.get(attachmentPath)
            if (!Files.exists(path) || !Files.isRegularFile(path)) {
                return "Attachment file not found: $attachmentPath"
            }
            if (Files.size(path) <= 0L) {
                return "Attachment file is empty: $attachmentPath"
            }
        }
        if (!attachmentBase64.isNullOrBlank() && attachmentFileName.isNullOrBlank()) {
            return "Attachment filename is required when using base64 content."
        }
        val attachment = when {
            !attachmentPath.isNullOrBlank() -> {
                val path = Paths.get(attachmentPath)
                val resolvedFileName = attachmentFileName?.takeIf { it.isNotBlank() }
                    ?: path.fileName.toString()
                val base64Content = Base64.getEncoder().encodeToString(Files.readAllBytes(path))
                Attachment.builder()
                    .content(base64Content)
                    .fileName(resolvedFileName)
                    .build()
            }
            !attachmentBase64.isNullOrBlank() -> {
                val resolvedFileName = attachmentFileName?.takeIf { it.isNotBlank() } ?: "attachment"
                Attachment.builder()
                    .content(attachmentBase64)
                    .fileName(resolvedFileName)
                    .build()
            }
            else -> null
        }
        val params = CreateEmailOptions.builder()
            .from(from)
            .to(to)
            .subject(subject)
            .html(html)
            .apply {
                if (attachment != null) {
                    attachments(attachment)
                }
            }
            .build()

        return try {
            val data = resend.emails().send(params)
            logger.info("Email sent successfully with ID: ${data.id}")
            "Email sent successfully."
        } catch (e: ResendException) {
            logger.error("Failed to send email: ${e.message}", e)
            "Failed to send email: ${e.message}"
        }
    }
}
