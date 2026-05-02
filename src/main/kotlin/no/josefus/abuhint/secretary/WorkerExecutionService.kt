package no.josefus.abuhint.secretary

import no.josefus.abuhint.agent.AgentRegistry
import no.josefus.abuhint.familie.FamilieChatService
import no.josefus.abuhint.repository.DeliveryWorkerAssistant
import no.josefus.abuhint.repository.GithubWorkerAssistant
import no.josefus.abuhint.repository.LangChain4jAssistant
import no.josefus.abuhint.repository.ResearchWorkerAssistant
import no.josefus.abuhint.repository.TechAdvisorAssistant
import no.josefus.abuhint.service.ChatIdContextHolder
import no.josefus.abuhint.service.TokenUsageContext
import no.josefus.abuhint.service.TokenUsageContextHolder
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.util.UUID

@Service
class WorkerExecutionService(
    private val researchWorker: ResearchWorkerAssistant,
    private val deliveryWorker: DeliveryWorkerAssistant,
    private val githubWorker: GithubWorkerAssistant,
    private val coachWorker: LangChain4jAssistant,
    private val techWorker: TechAdvisorAssistant,
    private val familieChatService: FamilieChatService,
) {
    fun runOpenAiWorker(
        agentId: String,
        workerMemoryId: String,
        brief: String,
        baseContext: TokenUsageContext,
    ): String {
        val uuid = UUID.randomUUID().toString()
        val dateTime = LocalDateTime.now().toString()
        val workerCtx = baseContext.copy(
            parentAgent = "SECRETARY",
            workerAgent = agentId.uppercase(),
            taskId = baseContext.taskId,
        )
        return withWorkerThreadLocals(workerMemoryId, workerCtx) {
            when (agentId) {
                AgentRegistry.IDs.RESEARCH ->
                    researchWorker.executeBrief(workerMemoryId, brief, uuid, dateTime)
                AgentRegistry.IDs.DELIVERY ->
                    deliveryWorker.executeBrief(workerMemoryId, brief, uuid, dateTime)
                AgentRegistry.IDs.GITHUB ->
                    githubWorker.executeBrief(workerMemoryId, brief, uuid, dateTime)
                AgentRegistry.IDs.COACH ->
                    coachWorker.chat(workerMemoryId, brief, uuid, dateTime)
                else -> throw IllegalArgumentException("Not an OpenAI-routed worker: $agentId")
            }
        }
    }

    fun runTechWorker(
        workerMemoryId: String,
        brief: String,
        baseContext: TokenUsageContext,
    ): String {
        val uuid = UUID.randomUUID().toString()
        val dateTime = LocalDateTime.now().toString()
        val workerCtx = baseContext.copy(
            parentAgent = "SECRETARY",
            workerAgent = AgentRegistry.IDs.TECH.uppercase(),
            taskId = baseContext.taskId,
        )
        return withWorkerThreadLocals(workerMemoryId, workerCtx) {
            techWorker.chat(workerMemoryId, brief, uuid, dateTime)
        }
    }

    fun runCalendarWorker(
        workerMemoryId: String,
        brief: String,
        userId: String,
        baseContext: TokenUsageContext,
    ): String {
        val workerCtx = baseContext.copy(
            parentAgent = "SECRETARY",
            workerAgent = AgentRegistry.IDs.CALENDAR.uppercase(),
            taskId = baseContext.taskId,
        )
        return withRestoredThreadLocalsAfter {
            familieChatService.processChat(
                chatId = workerMemoryId,
                userMessage = brief,
                userId = userId,
                metadata = null,
                usageContext = workerCtx,
            )
        }
    }

    /**
     * Ensures secretary (or caller) ChatId/token usage survives nested worker calls LangChain clears.
     */
    private fun <T> withWorkerThreadLocals(workerMemoryId: String, workerCtx: TokenUsageContext, block: () -> T): T {
        val prevChat = ChatIdContextHolder.get()
        val prevUsage = TokenUsageContextHolder.get()
        try {
            ChatIdContextHolder.set(workerMemoryId)
            TokenUsageContextHolder.set(workerCtx)
            return block()
        } finally {
            restore(prevChat, prevUsage)
        }
    }

    /**
     * [FamilieChatService.processChat] clears ThreadLocals internally; restore callers’ values afterwards.
     */
    private fun <T> withRestoredThreadLocalsAfter(block: () -> T): T {
        val prevChat = ChatIdContextHolder.get()
        val prevUsage = TokenUsageContextHolder.get()
        return try {
            block()
        } finally {
            restore(prevChat, prevUsage)
        }
    }

    private fun restore(prevChat: String?, prevUsage: TokenUsageContext?) {
        if (prevChat != null) {
            ChatIdContextHolder.set(prevChat)
        } else {
            ChatIdContextHolder.clear()
        }
        if (prevUsage != null) {
            TokenUsageContextHolder.set(prevUsage)
        } else {
            TokenUsageContextHolder.clear()
        }
    }
}
