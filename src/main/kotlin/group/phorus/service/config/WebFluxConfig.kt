package group.phorus.service.config

import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.webflux.autoconfigure.WebFluxRegistrations
import org.springframework.context.annotation.Bean
import org.springframework.data.web.ReactivePageableHandlerMethodArgumentResolver
import org.springframework.web.reactive.config.WebFluxConfigurer
import org.springframework.web.reactive.result.method.RequestMappingInfo
import org.springframework.web.reactive.result.method.annotation.ArgumentResolverConfigurer
import org.springframework.web.reactive.result.method.annotation.RequestMappingHandlerMapping
import java.lang.reflect.Method

/**
 * Autoconfigured WebFlux customizations.
 *
 * - Registers [ReactivePageableHandlerMethodArgumentResolver] so controllers can use
 *   `@PageableDefault` and [Pageable][org.springframework.data.domain.Pageable] parameters.
 * - Provides a custom [RequestMappingHandlerMapping] that filters out compiler-generated bridge
 *   and synthetic methods. When a controller extends `CrudController<ENTITY, DTO, RESPONSE>` and
 *   overrides methods like `findById`, the Kotlin compiler generates bridge methods to maintain
 *   JVM type erasure compatibility. Without filtering, Spring's default handler mapping will attempt
 *   to register both the real method and its bridge method as separate endpoints, causing
 *   duplicate registration errors.
 */
@AutoConfiguration
class WebFluxConfig : WebFluxConfigurer {
    override fun configureArgumentResolvers(configurer: ArgumentResolverConfigurer) {
        configurer.addCustomResolver(ReactivePageableHandlerMethodArgumentResolver())
    }

    @Bean
    fun webFluxRegistrations(): WebFluxRegistrations = object : WebFluxRegistrations {
        override fun getRequestMappingHandlerMapping(): RequestMappingHandlerMapping =
            object : RequestMappingHandlerMapping() {
                override fun getMappingForMethod(method: Method, handlerType: Class<*>): RequestMappingInfo? {
                    if (method.isBridge || method.isSynthetic) return null
                    return super.getMappingForMethod(method, handlerType)
                }
            }
    }
}
