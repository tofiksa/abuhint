package no.josefus.abuhint.configuration

import no.josefus.abuhint.secretary.SecretaryDelegationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.task.SimpleAsyncTaskExecutor

@Configuration
@EnableConfigurationProperties(SecretaryDelegationProperties::class)
class SecretaryAsyncConfiguration {

    @Bean
    fun secretaryWorkerExecutor(properties: SecretaryDelegationProperties): SimpleAsyncTaskExecutor {
        return SimpleAsyncTaskExecutor("secretary-worker-").apply {
            setVirtualThreads(true)
            concurrencyLimit = properties.maxConcurrent
        }
    }
}
