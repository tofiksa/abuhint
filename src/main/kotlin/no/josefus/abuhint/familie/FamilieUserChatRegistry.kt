package no.josefus.abuhint.familie

import java.util.concurrent.ConcurrentHashMap

/**
 * Maps LangChain4j chat memory id (same as API `chatId`) → authenticated user id (JWT subject)
 * for the duration of a Familieplanleggern request.
 *
 * Tool invocations often run on a worker thread where [SecurityContextHolder] is empty; the
 * memory id is still passed via [@ToolMemoryId][dev.langchain4j.agent.tool.ToolMemoryId], so
 * we can resolve OAuth credentials without relying on thread-local security state.
 */
object FamilieUserChatRegistry {
    private val chatIdToUserId = ConcurrentHashMap<String, String>()

    fun bind(chatId: String, userId: String) {
        chatIdToUserId[chatId] = userId
    }

    fun unbind(chatId: String) {
        chatIdToUserId.remove(chatId)
    }

    fun userIdFor(chatId: String): String? = chatIdToUserId[chatId]
}
