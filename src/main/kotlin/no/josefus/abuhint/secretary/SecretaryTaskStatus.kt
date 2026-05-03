package no.josefus.abuhint.secretary

enum class SecretaryTaskStatus {
    proposed,
    blocked,
    ready,
    delegated,
    waiting_for_confirmation,
    running,
    done,
    failed,
}
