package no.josefus.abuhint.tools

import dev.langchain4j.agent.tool.Tool
import org.apache.poi.xslf.usermodel.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.apache.poi.sl.usermodel.TextParagraph
import java.awt.Color
import java.awt.Rectangle
import java.io.FileOutputStream


@Serializable
data class SlideContent(
    val title: String,
    val bulletPoints: List<String> = emptyList(),
    val content: String = "",
    val slideType: SlideType = SlideType.TITLE_AND_CONTENT
)

@Serializable
enum class SlideType {
    TITLE_SLIDE,
    TITLE_AND_CONTENT,
    CONTENT_ONLY,
    BULLET_POINTS
}

@Serializable
data class PresentationRequest(
    val title: String,
    val slides: List<SlideContent>,
    val outputPath: String = "presentation.pptx",
    val theme: String = "default"
)

class PowerPointGeneratorTool {

    @Tool("Generer en PowerPoint-presentasjon med spesifiserte lysbilder og innhold")
    fun generatePresentation(
        title: String,
        slidesJson: String, // JSON string of slide content
        outputPath: String = "generated_presentation.pptx",
        confirm: Boolean = false
    ): String {
        if (!confirm) {
            return "I can generate the PowerPoint at $outputPath. Reply with confirmation to proceed."
        }
        if (outputPath.isBlank()) {
            return "Unable to generate presentation: outputPath is blank."
        }
        return try {
            val slides = parseSlideContent(slidesJson)
            createPresentation(title, slides, outputPath)
            "Successfully created PowerPoint presentation: $outputPath"
        } catch (e: Exception) {
            "Error creating presentation: ${e.message}"
        }
    }

    @Tool("Create a simple presentation with title and bullet points")
    fun createSimplePresentation(
        presentationTitle: String,
        slideTitle: String,
        bulletPoints: String, // Comma-separated bullet points
        outputPath: String = "simple_presentation.pptx",
        confirm: Boolean = false
    ): String {
        if (!confirm) {
            return "I can generate the PowerPoint at $outputPath. Reply with confirmation to proceed."
        }
        if (outputPath.isBlank()) {
            return "Unable to generate presentation: outputPath is blank."
        }
        return try {
            val points = bulletPoints.split(",").map { it.trim() }
            val slides = listOf(
                SlideContent(presentationTitle, slideType = SlideType.TITLE_SLIDE),
                SlideContent(slideTitle, points, slideType = SlideType.BULLET_POINTS)
            )
            createPresentation(presentationTitle, slides, outputPath)
            "Successfully created simple presentation: $outputPath"
        } catch (e: Exception) {
            "Error creating simple presentation: ${e.message}"
        }
    }

    private fun createPresentation(
        title: String,
        slides: List<SlideContent>,
        outputPath: String
    ) {
        val ppt = XMLSlideShow()

        slides.forEach { slideContent ->
            when (slideContent.slideType) {
                SlideType.TITLE_SLIDE -> createTitleSlide(ppt, slideContent)
                SlideType.TITLE_AND_CONTENT -> createTitleAndContentSlide(ppt, slideContent)
                SlideType.BULLET_POINTS -> createBulletPointSlide(ppt, slideContent)
                SlideType.CONTENT_ONLY -> createContentSlide(ppt, slideContent)
            }
        }

        // Save the presentation
        FileOutputStream(outputPath).use { out ->
            ppt.write(out)
        }
        ppt.close()
    }

    private fun createTitleSlide(ppt: XMLSlideShow, content: SlideContent) {
        val slide = ppt.createSlide()

        // Title
        val titleShape = slide.createTextBox()
        titleShape.anchor = Rectangle(50, 100, 600, 100)
        val titleParagraph = titleShape.addNewTextParagraph()
        titleParagraph.textAlign = TextParagraph.TextAlign.CENTER
        val titleRun = titleParagraph.addNewTextRun()
        titleRun.setText(content.title)
        titleRun.fontSize = 44.0
        titleRun.isBold = true
        titleRun.setFontColor(Color.DARK_GRAY)

        // Subtitle if content exists
        if (content.content.isNotEmpty()) {
            val subtitleShape = slide.createTextBox()
            subtitleShape.anchor = Rectangle(50, 250, 600, 50)
            val subtitleParagraph = subtitleShape.addNewTextParagraph()
            subtitleParagraph.textAlign = TextParagraph.TextAlign.CENTER
            val subtitleRun = subtitleParagraph.addNewTextRun()
            subtitleRun.setText(content.content)
            subtitleRun.fontSize = 24.0
            subtitleRun.setFontColor(Color.DARK_GRAY)
        }
    }

    private fun createTitleAndContentSlide(ppt: XMLSlideShow, content: SlideContent) {
        val slide = ppt.createSlide()

        // Title
        val titleShape = slide.createTextBox()
        titleShape.anchor = Rectangle(50, 50, 600, 80)
        val titleParagraph = titleShape.addNewTextParagraph()
        val titleRun = titleParagraph.addNewTextRun()
        titleRun.setText(content.title)
        titleRun.fontSize = 32.0
        titleRun.isBold = true
        titleRun.setFontColor(Color.DARK_GRAY)

        // Content
        val contentShape = slide.createTextBox()
        contentShape.anchor = Rectangle(50, 150, 600, 350)
        val contentParagraph = contentShape.addNewTextParagraph()
        val contentRun = contentParagraph.addNewTextRun()
        contentRun.setText(content.content)
        contentRun.fontSize = 18.0
        contentRun.setFontColor(Color.BLACK)
    }

    private fun createBulletPointSlide(ppt: XMLSlideShow, content: SlideContent) {
        val slide = ppt.createSlide()

        // Title
        val titleShape = slide.createTextBox()
        titleShape.anchor = Rectangle(50, 50, 600, 80)
        val titleParagraph = titleShape.addNewTextParagraph()
        val titleRun = titleParagraph.addNewTextRun()
        titleRun.setText(content.title)
        titleRun.fontSize = 32.0
        titleRun.isBold = true
        titleRun.setFontColor(Color.DARK_GRAY)

        // Bullet points
        val bulletShape = slide.createTextBox()
        bulletShape.anchor = Rectangle(50, 150, 600, 350)

        content.bulletPoints.forEach { point ->
            val bulletParagraph = bulletShape.addNewTextParagraph()
            bulletParagraph.setBullet(true)
            bulletParagraph.bulletCharacter = "•"
            bulletParagraph.leftMargin = 20.0
            bulletParagraph.indent = 20.0

            val bulletRun = bulletParagraph.addNewTextRun()
            bulletRun.setText(point)
            bulletRun.fontSize = 20.0
            bulletRun.setFontColor(Color.BLACK)
        }
    }

    private fun createContentSlide(ppt: XMLSlideShow, content: SlideContent) {
        val slide = ppt.createSlide()

        val contentShape = slide.createTextBox()
        contentShape.anchor = Rectangle(50, 100, 600, 400)
        val contentParagraph = contentShape.addNewTextParagraph()
        contentParagraph.textAlign = TextParagraph.TextAlign.LEFT
        val contentRun = contentParagraph.addNewTextRun()
        contentRun.setText(content.content)
        contentRun.fontSize = 24.0
        contentRun.setFontColor(Color.BLACK)
    }

    private fun parseSlideContent(jsonString: String): List<SlideContent> {
        return try {
            val json = Json { ignoreUnknownKeys = true }
            json.decodeFromString<List<SlideContent>>(jsonString)
        } catch (e: Exception) {
            // Fallback: try to parse as a simple string list
            try {
                val lines = jsonString.split("\n").filter { it.isNotBlank() }
                lines.mapIndexed { index, line ->
                    if (index == 0) {
                        SlideContent(line, slideType = SlideType.TITLE_SLIDE)
                    } else {
                        SlideContent("Slide ${index}", content = line, slideType = SlideType.TITLE_AND_CONTENT)
                    }
                }
            } catch (e2: Exception) {
                listOf(SlideContent("Error", listOf("Failed to parse slide content: ${e.message}")))
            }
        }
    }
}
