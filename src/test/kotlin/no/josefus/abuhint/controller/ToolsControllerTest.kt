package no.josefus.abuhint.controller

import dev.langchain4j.agent.tool.Tool
import no.josefus.abuhint.dto.ToolInfo
import no.josefus.abuhint.dto.ToolsResponse
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.context.ApplicationContext
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ToolsControllerTest {

    class FakeTool {
        @Tool(name = "doSomething", value = ["Does something useful"])
        fun doSomething(input: String): String = "done"

        @Tool("Does another thing")
        fun doAnother(): String = "another"
    }

    private fun buildContext(beans: Map<String, Any>): ApplicationContext {
        val ctx = mock(ApplicationContext::class.java)
        `when`(ctx.beanDefinitionNames).thenReturn(beans.keys.toTypedArray())
        beans.forEach { (name, bean) -> `when`(ctx.getBean(name)).thenReturn(bean) }
        return ctx
    }

    @Test
    fun `listTools returns tools from annotated methods`() {
        val ctx = buildContext(mapOf("fakeTool" to FakeTool()))
        val controller = ToolsController(ctx)

        val response = controller.listTools()
        val tools: List<ToolInfo> = response.body!!.tools

        assertEquals(2, tools.size)
        assertTrue(tools.any { it.name == "doSomething" && it.description == "Does something useful" })
        assertTrue(tools.any { it.name == "doAnother" && it.description == "Does another thing" })
    }

    @Test
    fun `listTools deduplicates tools with the same name`() {
        val ctx = buildContext(mapOf("a" to FakeTool(), "b" to FakeTool()))
        val controller = ToolsController(ctx)

        val tools = controller.listTools().body!!.tools
        assertEquals(2, tools.size)
    }

    @Test
    fun `listTools returns sorted list`() {
        val ctx = buildContext(mapOf("fakeTool" to FakeTool()))
        val controller = ToolsController(ctx)

        val names = controller.listTools().body!!.tools.map { it.name }
        assertEquals(names.sorted(), names)
    }

    @Test
    fun `listTools handles beans that fail to load gracefully`() {
        val ctx = mock(ApplicationContext::class.java)
        `when`(ctx.beanDefinitionNames).thenReturn(arrayOf("broken"))
        `when`(ctx.getBean("broken")).thenThrow(RuntimeException("boom"))

        val controller = ToolsController(ctx)
        val response = controller.listTools()
        assertTrue(response.statusCode.is2xxSuccessful)
        assertTrue(response.body!!.tools.isEmpty())
    }
}
