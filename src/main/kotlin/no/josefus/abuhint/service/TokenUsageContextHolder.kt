package no.josefus.abuhint.service

object TokenUsageContextHolder {
    private val holder = ThreadLocal<TokenUsageContext>()

    fun set(context: TokenUsageContext) = holder.set(context)
    fun get(): TokenUsageContext? = holder.get()
    fun clear() = holder.remove()
}
