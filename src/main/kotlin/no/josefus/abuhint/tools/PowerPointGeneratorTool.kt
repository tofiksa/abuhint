package no.josefus.abuhint.tools

import dev.langchain4j.agent.tool.P
import dev.langchain4j.agent.tool.Tool
import no.josefus.abuhint.service.S3FileLinkService
import org.apache.poi.sl.usermodel.TextParagraph.TextAlign
import org.apache.poi.xslf.usermodel.XMLSlideShow
import org.apache.poi.xslf.usermodel.XSLFSlide
import org.apache.poi.xslf.usermodel.XSLFTextBox
import org.apache.poi.xslf.usermodel.XSLFTextRun
import java.awt.Color
import java.awt.geom.Rectangle2D
import java.io.FileOutputStream
import java.nio.file.Files

enum class BlockType { PARAGRAPH, BULLET }

data class ContentBlock(
    val text: String,
    val type: BlockType = BlockType.BULLET,
    val level: Int = 1  // 1 = top-level bullet, 2 = sub-bullet (only used for BULLET blocks)
)

data class Slide(
    val title: String,
    val subtitle: String = "",                  // shown below the title on the cover slide
    val blocks: List<ContentBlock> = emptyList()
)

class PowerPointGeneratorTool(
    private val s3FileLinkService: S3FileLinkService
) {
    private val pptxContentType =
        "application/vnd.openxmlformats-officedocument.presentationml.presentation"

    @Tool(
        "Generate a PowerPoint presentation and upload it for download. " +
        "The FIRST slide is always the cover slide — set its 'subtitle' for a subheading. " +
        "All other slides have a 'title' and a list of 'blocks'. " +
        "Each block is either PARAGRAPH (prose text) or BULLET (bullet point). " +
        "Use level=1 for top-level bullets and level=2 for sub-bullets. " +
        "Blocks appear in the order given, so paragraphs and bullets can be freely mixed on one slide."
    )
    fun generatePresentation(
        @P("Title of the presentation, also used as the filename") presentationTitle: String,
        @P("Slides in display order. First slide is the cover.") slides: List<Slide>,
        @P("Set to true once the user has confirmed they want to proceed") confirm: Boolean = false
    ): String {
        if (!confirm) {
            val summary = slides.joinToString(", ") { "\"${it.title}\"" }
            return "I'll create a ${ slides.size }-slide presentation \"$presentationTitle\": $summary. Confirm to proceed."
        }
        if (!s3FileLinkService.isConfigured()) {
            return "Unable to generate presentation: S3 storage is not configured."
        }
        return try {
            val url = buildAndUpload(presentationTitle, slides)
                ?: return "Failed to upload the presentation."
            "Your presentation is ready: $url"
        } catch (e: Exception) {
            "Error generating presentation: ${e.message}"
        }
    }

    /** Called directly by PowerPointEmailTool — bypasses the confirm gate. */
    internal fun buildAndUpload(presentationTitle: String, slides: List<Slide>): String? {
        if (!s3FileLinkService.isConfigured()) return null
        val fileName = presentationTitle.replace(Regex("[^A-Za-z0-9_-]"), "_").lowercase() + ".pptx"
        val tempPath = Files.createTempFile("abuhint-pptx-", ".pptx")
        return try {
            buildPresentation(slides, tempPath.toString())
            s3FileLinkService.uploadAndPresign(tempPath, fileName, pptxContentType)
        } finally {
            Files.deleteIfExists(tempPath)
        }
    }

    private fun buildPresentation(slides: List<Slide>, outputPath: String) {
        XMLSlideShow().use { ppt ->
            val W = ppt.pageSize.width.toDouble()
            val H = ppt.pageSize.height.toDouble()
            slides.forEachIndexed { i, slide ->
                if (i == 0) renderCoverSlide(ppt, slide, W, H)
                else renderContentSlide(ppt, slide, W, H)
            }
            FileOutputStream(outputPath).use { ppt.write(it) }
        }
    }

    // ── Slide renderers ────────────────────────────────────────────────────────

    private fun renderCoverSlide(ppt: XMLSlideShow, slide: Slide, W: Double, H: Double) {
        val s = ppt.createSlide()
        textBox(s, W * 0.10, H * 0.30, W * 0.80, H * 0.25) { box ->
            paragraph(box, TextAlign.CENTER) {
                setText(slide.title)
                fontSize = 40.0
                isBold = true
                setFontColor(Color(30, 30, 80))
            }
        }
        if (slide.subtitle.isNotBlank()) {
            textBox(s, W * 0.10, H * 0.58, W * 0.80, H * 0.15) { box ->
                paragraph(box, TextAlign.CENTER) {
                    setText(slide.subtitle)
                    fontSize = 24.0
                    setFontColor(Color.DARK_GRAY)
                }
            }
        }
    }

    private fun renderContentSlide(ppt: XMLSlideShow, slide: Slide, W: Double, H: Double) {
        val s = ppt.createSlide()

        // Slide title bar
        textBox(s, W * 0.05, H * 0.04, W * 0.90, H * 0.16) { box ->
            paragraph(box) {
                setText(slide.title)
                fontSize = 28.0
                isBold = true
                setFontColor(Color(30, 30, 80))
            }
        }

        if (slide.blocks.isEmpty()) return

        // Content area — all blocks share one text box so bullets and paragraphs
        // flow together naturally and stay vertically aligned.
        textBox(s, W * 0.05, H * 0.22, W * 0.90, H * 0.72) { box ->
            slide.blocks.forEach { block ->
                when (block.type) {
                    BlockType.PARAGRAPH -> paragraph(box) {
                        setText(block.text)
                        fontSize = 18.0
                        setFontColor(Color.BLACK)
                    }
                    BlockType.BULLET -> {
                        val isSubBullet = block.level >= 2
                        val para = box.addNewTextParagraph()
                        para.setBullet(true)
                        para.bulletCharacter = if (isSubBullet) "–" else "•"
                        para.leftMargin = if (isSubBullet) 55.0 else 28.0
                        para.indent = -15.0
                        para.addNewTextRun().apply {
                            setText(block.text)
                            fontSize = if (isSubBullet) 16.0 else 18.0
                            setFontColor(Color.BLACK)
                        }
                    }
                }
            }
        }
    }

    // ── Layout helpers ─────────────────────────────────────────────────────────

    private fun textBox(
        slide: XSLFSlide,
        x: Double, y: Double, w: Double, h: Double,
        configure: (XSLFTextBox) -> Unit
    ) {
        val box = slide.createTextBox()
        box.anchor = Rectangle2D.Double(x, y, w, h)
        configure(box)
    }

    private fun paragraph(
        box: XSLFTextBox,
        align: TextAlign = TextAlign.LEFT,
        configure: XSLFTextRun.() -> Unit
    ) {
        val para = box.addNewTextParagraph()
        para.textAlign = align
        para.addNewTextRun().configure()
    }
}
