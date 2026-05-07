package group.phorus.service.config

import org.springframework.boot.EnvironmentPostProcessor
import org.springframework.boot.SpringApplication
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.core.env.ConfigurableEnvironment
import org.springframework.core.env.MapPropertySource
import org.springframework.core.env.StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME

/**
 * Spring [EnvironmentPostProcessor] that sets sensible default configuration values for the consuming service.
 *
 * This class runs very early in the Spring Boot startup process, before the application context is created.
 * It injects default property values into the environment at the lowest precedence level, meaning any
 * configuration in `application.yml`, `application.properties`, or environment variables will override these defaults.
 *
 * ## Defaults Applied
 *
 * **server.shutdown=graceful**
 *
 * Enables graceful shutdown, which means when the application receives a shutdown signal (SIGTERM), it:
 * 1. Stops accepting new requests immediately
 * 2. Waits for all in-flight requests to complete (up to a timeout)
 * 3. Only then shuts down the application
 *
 * **spring.jackson.default-property-inclusion=NON_NULL**
 *
 * Configures Jackson (the JSON serialization library) to omit null fields from JSON responses.
 */
@Order(Ordered.LOWEST_PRECEDENCE)
open class PostProcessorConfig : EnvironmentPostProcessor {
    override fun postProcessEnvironment(
        environment: ConfigurableEnvironment,
        application: SpringApplication
    ) {
        environment.propertySources.addAfter(SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
            MapPropertySource("prefixed", mapOf(
                "server.shutdown" to "graceful",
                "spring.jackson.default-property-inclusion" to "NON_NULL",
            ))
        )
    }
}