package no.josefus.abuhint.agent

/**
 * Describes a delegatable LangChain/OpenAI-advanced worker wired in [WorkerExecutionService].
 */
data class AgentDescriptor(
    val id: String,
    val displayName: String,
    val description: String,
    val mutatesExternally: Boolean,
    val riskLevel: WorkerRiskLevel,
    val capabilities: List<String>,
    /** Spring @Bean names of tools on the worker [dev.langchain4j.service.spring.AiService], if any. */
    val workerToolBeans: List<String>,
)
