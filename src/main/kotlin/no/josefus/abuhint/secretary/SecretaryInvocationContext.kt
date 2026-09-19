package no.josefus.abuhint.secretary

import dev.langchain4j.invocation.InvocationParameters
import no.josefus.abuhint.service.TokenUsageContext

/** Server-supplied context that follows an assistant invocation across streaming/tool threads. */
object SecretaryInvocationContext {
    private const val KEY = "secretaryTokenUsageContext"

    fun from(context: TokenUsageContext): InvocationParameters = InvocationParameters.from(KEY, context)

    fun require(parameters: InvocationParameters): TokenUsageContext =
        parameters.get<TokenUsageContext>(KEY)?.takeIf { it.userId.isNotBlank() }
            ?: throw IllegalStateException("Mangler autentisert sekretærkontekst")
}
