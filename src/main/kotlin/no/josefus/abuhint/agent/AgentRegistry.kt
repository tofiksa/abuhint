package no.josefus.abuhint.agent

import org.springframework.stereotype.Component

@Component
class AgentRegistry {

    val agents: List<AgentDescriptor> = listOf(
        AgentDescriptor(
            id = IDs.RESEARCH,
            displayName = "Research",
            description = "Kort nett-research og oppsummering med webSearchTool.",
            mutatesExternally = false,
            riskLevel = WorkerRiskLevel.LOW,
            capabilities = listOf("web_search"),
            workerToolBeans = listOf("webSearchTool"),
        ),
        AgentDescriptor(
            id = IDs.DELIVERY,
            displayName = "Delivery",
            description = "E-post og PowerPoint (generere og sende). Krever ofte brukerbekreftelse.",
            mutatesExternally = true,
            riskLevel = WorkerRiskLevel.MEDIUM,
            capabilities = listOf("email", "powerpoint"),
            workerToolBeans = listOf("emailService", "powerPointTool", "powerPointEmailTool"),
        ),
        AgentDescriptor(
            id = IDs.GITHUB,
            displayName = "GitHub",
            description = "GitHub-operasjoner (gren, commit, PR).",
            mutatesExternally = true,
            riskLevel = WorkerRiskLevel.HIGH,
            capabilities = listOf("github"),
            workerToolBeans = listOf("gitHubService"),
        ),
        AgentDescriptor(
            id = IDs.COACH,
            displayName = "Coach",
            description = "Teamcoach med Abu-hints verktøysett (som tidligere hovedagent).",
            mutatesExternally = true,
            riskLevel = WorkerRiskLevel.MEDIUM,
            capabilities = listOf("web_search", "email", "powerpoint"),
            workerToolBeans = listOf("emailService", "powerPointTool", "powerPointEmailTool", "webSearchTool"),
        ),
        AgentDescriptor(
            id = IDs.TECH,
            displayName = "TechAdvisor",
            description = "Teknisk rådgivning med Gemini og web-søk.",
            mutatesExternally = false,
            riskLevel = WorkerRiskLevel.LOW,
            capabilities = listOf("web_search", "gemini"),
            workerToolBeans = listOf("webSearchTool"),
        ),
        AgentDescriptor(
            id = IDs.CALENDAR,
            displayName = "Familieplanleggern",
            description = "Google Kalender via Familieplanleggern (propose/confirm for mutasjoner).",
            mutatesExternally = true,
            riskLevel = WorkerRiskLevel.MEDIUM,
            capabilities = listOf("calendar"),
            workerToolBeans = emptyList(),
        ),
    )

    private val byId: Map<String, AgentDescriptor> = agents.associateBy { it.id }

    fun get(id: String): AgentDescriptor? = byId[id]

    fun require(id: String): AgentDescriptor =
        get(id) ?: throw IllegalArgumentException("Unknown worker agent id: $id")

    object IDs {
        const val RESEARCH = "research"
        const val DELIVERY = "delivery"
        const val GITHUB = "github"
        const val COACH = "coach"
        const val TECH = "tech"
        const val CALENDAR = "calendar"
    }
}
