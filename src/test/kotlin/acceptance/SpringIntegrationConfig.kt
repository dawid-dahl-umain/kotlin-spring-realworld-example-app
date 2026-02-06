package acceptance

import acceptance.driver.http.HttpProtocolDriver
import io.cucumber.spring.CucumberContextConfiguration
import io.realworld.ApiApplication
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import

@CucumberContextConfiguration
@SpringBootTest(classes = [ApiApplication::class], webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(HttpProtocolDriver::class, DslConfig::class)
class SpringIntegrationConfig
