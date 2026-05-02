package no.josefus.abuhint.secretary

object SecretaryChatIds {
    private const val PREFIX = "secretary-"

    fun memoryId(clientChatId: String): String =
        clientChatId.trim().let { id ->
            if (id.startsWith(PREFIX)) id else "$PREFIX$id"
        }

    fun workerMemoryId(taskId: String): String = "worker-$taskId"

    fun familieWorkerMemoryId(taskId: String): String = "familie-worker-$taskId"

    /**
     * Client-facing chat id used in DB and APIs (without secretary- prefix).
     */
    fun clientChatIdFromMemory(memoryId: String): String {
        val id = memoryId.trim()
        return if (id.startsWith(PREFIX)) id.removePrefix(PREFIX) else id
    }
}
