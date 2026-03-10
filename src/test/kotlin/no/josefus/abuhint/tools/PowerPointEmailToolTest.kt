package no.josefus.abuhint.tools

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.nio.file.Files
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertEquals

class PowerPointEmailToolTest {

    private lateinit var emailService: EmailService
    private lateinit var powerPointGeneratorTool: PowerPointGeneratorTool
    private lateinit var tool: PowerPointEmailTool

    private val slides = listOf(
        Slide(title = "Cover", subtitle = "Test presentation"),
        Slide(
            title = "Content",
            blocks = listOf(ContentBlock("Point one", BlockType.BULLET))
        )
    )

    @BeforeEach
    fun setup() {
        emailService = mock()
        powerPointGeneratorTool = mock()
        tool = PowerPointEmailTool(emailService, powerPointGeneratorTool, "")
    }

    @Test
    fun `generateAndEmail returns confirmation when confirm is false`() {
        val result = tool.generateAndEmail(
            to = "test@example.com",
            presentationTitle = "My Presentation",
            slides = slides,
            confirm = false
        )

        assertTrue(result.contains("My Presentation"), "Should mention the title")
        assertTrue(result.contains("2 slides"), "Should mention slide count")
        assertTrue(result.contains("test@example.com"), "Should mention recipient")
        assertTrue(result.contains("Confirm"), "Should ask for confirmation")
    }

    @Test
    fun `generateAndEmail builds pptx and sends email with attachment when confirmed`() {
        val tempFile = Files.createTempFile("test-pptx-", ".pptx")
        Files.write(tempFile, byteArrayOf(0x50, 0x4B, 0x03, 0x04))

        whenever(powerPointGeneratorTool.buildToTempFile(any(), any())).thenReturn(tempFile)
        whenever(powerPointGeneratorTool.uploadAndPresign(any(), any()))
            .thenReturn("https://s3.example.com/my_presentation.pptx")
        whenever(emailService.sendEmail(
            html = any(), to = any(),
            attachmentPath = anyOrNull(), attachmentBase64 = anyOrNull(),
            attachmentFileName = anyOrNull(), attachmentContentType = anyOrNull(),
            confirm = any()
        )).thenReturn("Email sent successfully.")

        val result = tool.generateAndEmail(
            to = "test@example.com",
            presentationTitle = "My Presentation",
            slides = slides,
            confirm = true
        )

        assertEquals("Email sent successfully.", result)
        verify(powerPointGeneratorTool).buildToTempFile("My Presentation", slides)

        val pathCaptor = argumentCaptor<String>()
        val fileNameCaptor = argumentCaptor<String>()
        val confirmCaptor = argumentCaptor<Boolean>()
        verify(emailService).sendEmail(
            html = any(), to = eq("test@example.com"),
            attachmentPath = pathCaptor.capture(), attachmentBase64 = anyOrNull(),
            attachmentFileName = fileNameCaptor.capture(), attachmentContentType = anyOrNull(),
            confirm = confirmCaptor.capture()
        )
        assertEquals(tempFile.toString(), pathCaptor.firstValue, "Should pass temp file as attachment path")
        assertEquals("my_presentation.pptx", fileNameCaptor.firstValue, "Should use sanitized filename")
        assertTrue(confirmCaptor.firstValue, "Should pass confirm=true to email service")
        assertFalse(Files.exists(tempFile), "Temp file should be deleted after sending")
    }

    @Test
    fun `generateAndEmail sends email without download link when S3 upload fails`() {
        val tempFile = Files.createTempFile("test-pptx-", ".pptx")
        Files.write(tempFile, byteArrayOf(0x50, 0x4B, 0x03, 0x04))

        whenever(powerPointGeneratorTool.buildToTempFile(any(), any())).thenReturn(tempFile)
        whenever(powerPointGeneratorTool.uploadAndPresign(any(), any())).thenReturn(null)
        whenever(emailService.sendEmail(
            html = any(), to = any(),
            attachmentPath = anyOrNull(), attachmentBase64 = anyOrNull(),
            attachmentFileName = anyOrNull(), attachmentContentType = anyOrNull(),
            confirm = any()
        )).thenReturn("Email sent successfully.")

        val result = tool.generateAndEmail(
            to = "test@example.com",
            presentationTitle = "My Presentation",
            slides = slides,
            confirm = true
        )

        assertEquals("Email sent successfully.", result)
        assertFalse(Files.exists(tempFile), "Temp file should be deleted even when S3 fails")
    }

    @Test
    fun `generateAndEmail returns error when pptx generation fails`() {
        whenever(powerPointGeneratorTool.buildToTempFile(any(), any()))
            .thenThrow(RuntimeException("POI error"))

        val result = tool.generateAndEmail(
            to = "test@example.com",
            presentationTitle = "My Presentation",
            slides = slides,
            confirm = true
        )

        assertTrue(result.contains("Failed to generate"), "Should report generation failure")
        assertTrue(result.contains("POI error"), "Should include error message")
    }

    @Test
    fun `generateAndEmail cleans up temp file even when email sending fails`() {
        val tempFile = Files.createTempFile("test-pptx-", ".pptx")
        Files.write(tempFile, byteArrayOf(0x50, 0x4B, 0x03, 0x04))

        whenever(powerPointGeneratorTool.buildToTempFile(any(), any())).thenReturn(tempFile)
        whenever(powerPointGeneratorTool.uploadAndPresign(any(), any())).thenReturn(null)
        whenever(emailService.sendEmail(
            html = any(), to = any(),
            attachmentPath = anyOrNull(), attachmentBase64 = anyOrNull(),
            attachmentFileName = anyOrNull(), attachmentContentType = anyOrNull(),
            confirm = any()
        )).thenReturn("Failed to send email: API error")

        val result = tool.generateAndEmail(
            to = "test@example.com",
            presentationTitle = "My Presentation",
            slides = slides,
            confirm = true
        )

        assertEquals("Failed to send email: API error", result)
        assertFalse(Files.exists(tempFile), "Temp file should be deleted even when email fails")
    }

    @Test
    fun `generateAndEmail email html includes download link when S3 succeeds`() {
        val tempFile = Files.createTempFile("test-pptx-", ".pptx")
        Files.write(tempFile, byteArrayOf(0x50, 0x4B, 0x03, 0x04))
        val htmlCaptor = argumentCaptor<String>()

        whenever(powerPointGeneratorTool.buildToTempFile(any(), any())).thenReturn(tempFile)
        whenever(powerPointGeneratorTool.uploadAndPresign(any(), any()))
            .thenReturn("https://s3.example.com/download.pptx")
        whenever(emailService.sendEmail(
            html = htmlCaptor.capture(), to = any(),
            attachmentPath = anyOrNull(), attachmentBase64 = anyOrNull(),
            attachmentFileName = anyOrNull(), attachmentContentType = anyOrNull(),
            confirm = any()
        )).thenReturn("Email sent successfully.")

        tool.generateAndEmail(
            to = "test@example.com",
            presentationTitle = "My Presentation",
            slides = slides,
            confirm = true
        )

        val html = htmlCaptor.firstValue
        assertTrue(html.contains("attached to this email"), "HTML should mention attachment")
        assertTrue(html.contains("https://s3.example.com/download.pptx"), "HTML should include download link")
        assertTrue(html.contains("my_presentation.pptx"), "HTML should include filename")
    }
}
