package acceptance

import acceptance.dsl.utils.DslContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Scope

@Configuration
class DslConfig {
    @Bean
    @Scope("cucumber-glue")
    fun dslContext() = DslContext()
}
