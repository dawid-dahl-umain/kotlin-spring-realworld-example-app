package acceptance.driver.http

import acceptance.driver.ProtocolDriver
import org.junit.jupiter.api.Assertions.assertNotNull
import org.springframework.core.env.Environment
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate

data class TagsResponse(val tags: List<String> = emptyList())

@Component
class HttpProtocolDriver(private val environment: Environment) : ProtocolDriver {

    private val restTemplate = RestTemplate()
    private val baseUrl: String get() = "http://localhost:${environment.getProperty("local.server.port")}"

    private var tags: List<String>? = null

    override fun getTags() {
        val response = restTemplate.getForEntity("$baseUrl/api/tags", TagsResponse::class.java)
        if (response.statusCode != HttpStatus.OK) {
            error("GET /api/tags returned ${response.statusCode}")
        }
        tags = response.body?.tags ?: emptyList()
    }

    override fun verifyIsTagList() {
        assertNotNull(tags)
    }
}
