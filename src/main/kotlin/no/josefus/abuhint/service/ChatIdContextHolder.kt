package no.josefus.abuhint.service

object ChatIdContextHolder {
    private val holder = ThreadLocal<String>()

    fun set(chatId: String) = holder.set(chatId)
    fun get(): String? = holder.get()
    fun clear() = holder.remove()
}
