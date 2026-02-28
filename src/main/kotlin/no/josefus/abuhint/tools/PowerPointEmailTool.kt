package no.josefus.abuhint.tools

import dev.langchain4j.agent.tool.Tool
import org.springframework.stereotype.Component
import java.nio.file.Paths

@Component
class PowerPointEmailTool(
    private val emailService: EmailService,
    private val powerPointGeneratorTool: PowerPointGeneratorTool
) {

    @Tool("Generate a PowerPoint presentation and email it as an attachment")
    fun generatePresentationAndEmail(
        to: String,
        title: String,
        slidesJson: String,
        outputPath: String = "generated_presentation.pptx",
        emailHtml: String = "<p>Attached is your presentation.</p>",
        confirm: Boolean = false
    ): String {
        if (!confirm) {
            return "I can generate the PowerPoint at $outputPath and email it to $to. Reply with confirmation to proceed."
        }
        val generationResult = powerPointGeneratorTool.generatePresentation(
            title = title,
            slidesJson = slidesJson,
            outputPath = outputPath,
            confirm = true
        )
        if (!generationResult.startsWith("Successfully created PowerPoint presentation:")) {
            return generationResult
        }
        val fileName = Paths.get(outputPath).fileName.toString()
        return emailService.sendEmail(
            html = emailHtml,
            to = to,
            attachmentPath = outputPath,
            attachmentFileName = fileName,
            attachmentContentType = "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            confirm = true
        )
    }
}
