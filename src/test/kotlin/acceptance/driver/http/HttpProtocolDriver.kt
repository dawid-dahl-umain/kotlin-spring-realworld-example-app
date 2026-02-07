package acceptance.driver.http

import acceptance.driver.ProtocolDriver
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.junit.jupiter.api.Assertions.*
import org.springframework.core.env.Environment
import org.springframework.http.*
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestTemplate

// --- Response DTOs ---

data class TagsResponse(val tags: List<String> = emptyList())

data class UserResponse(val user: UserBody? = null)
data class UserBody(
    val email: String = "",
    val token: String = "",
    val username: String = "",
    val bio: String = "",
    val image: String = ""
)

data class ArticleResponse(val article: ArticleBody? = null)
data class ArticleBody(
    val slug: String = "",
    val title: String = "",
    val description: String = "",
    val body: String = "",
    val tagList: List<String> = emptyList(),
    val createdAt: String = "",
    val updatedAt: String = "",
    val favorited: Boolean = false,
    val favoritesCount: Int = 0
)

data class ArticlesResponse(
    val articles: List<ArticleBody> = emptyList(),
    val articlesCount: Int = 0
)

data class ErrorResponse(val errors: Map<String, List<String>> = emptyMap())

// --- Last response holder ---

data class LastResponse(
    val statusCode: HttpStatusCode,
    val body: String
)

@Component
class HttpProtocolDriver(private val environment: Environment) : ProtocolDriver {

    private val restTemplate = RestTemplate()
    private val mapper = jacksonObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    private val baseUrl: String get() = "http://localhost:${environment.getProperty("local.server.port")}"

    private var lastResponse: LastResponse? = null
    private var authToken: String? = null

    // --- Helpers ---

    private fun authHeaders(): HttpHeaders {
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        authToken?.let { headers.set("Authorization", "Token $it") }
        return headers
    }

    private fun jsonHeaders(): HttpHeaders {
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        return headers
    }

    private fun executeRequest(method: HttpMethod, url: String, body: Any? = null, headers: HttpHeaders = jsonHeaders()) {
        val entity = HttpEntity(body, headers)
        try {
            val response = restTemplate.exchange(url, method, entity, String::class.java)
            lastResponse = LastResponse(response.statusCode, response.body ?: "")
        } catch (e: HttpClientErrorException) {
            lastResponse = LastResponse(e.statusCode, e.responseBodyAsString)
        }
    }

    private inline fun <reified T> parseLastResponse(): T =
        mapper.readValue(lastResponse!!.body)

    // --- Tags ---

    override fun getTags() {
        executeRequest(HttpMethod.GET, "$baseUrl/api/tags")
    }

    override fun confirmTagListContains(tag: String) {
        assertEquals(HttpStatus.OK, lastResponse!!.statusCode)
        val tags = parseLastResponse<TagsResponse>().tags
        assertTrue(tags.contains(tag), "Expected tag '$tag' in list: $tags")
    }

    // --- User registration ---

    override fun registerUser(username: String, email: String, password: String) {
        val body = mapOf("user" to mapOf("username" to username, "email" to email, "password" to password))
        executeRequest(HttpMethod.POST, "$baseUrl/api/users", body)
    }

    override fun confirmUserRegistered() {
        assertEquals(HttpStatus.OK, lastResponse!!.statusCode)
        val user = parseLastResponse<UserResponse>().user
        assertNotNull(user, "Expected user in response")
        assertTrue(user!!.token.isNotEmpty(), "Expected non-empty JWT token")
    }

    override fun confirmRegistrationError(field: String) {
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, lastResponse!!.statusCode)
        val errors = parseLastResponse<ErrorResponse>().errors
        assertTrue(
            errors.containsKey(field),
            "Expected error on field '$field' but got errors on: ${errors.keys}"
        )
    }

    // --- Authentication ---

    override fun registerAndLogin(username: String, email: String, password: String) {
        registerUser(username, email, password)
        assertEquals(HttpStatus.OK, lastResponse!!.statusCode, "Registration failed during login setup")
        authToken = parseLastResponse<UserResponse>().user!!.token
    }

    override fun loginWithToken(token: String) {
        authToken = token
    }

    // --- Article creation & retrieval ---

    override fun createArticle(title: String, description: String, body: String, tags: List<String>) {
        val articleBody = mutableMapOf<String, Any>(
            "title" to title,
            "description" to description,
            "body" to body
        )
        if (tags.isNotEmpty()) {
            articleBody["tagList"] = tags
        }
        val requestBody = mapOf("article" to articleBody)
        executeRequest(HttpMethod.POST, "$baseUrl/api/articles", requestBody, authHeaders())
    }

    override fun getArticleBySlug(slug: String) {
        executeRequest(HttpMethod.GET, "$baseUrl/api/articles/$slug")
    }

    override fun getArticles() {
        executeRequest(HttpMethod.GET, "$baseUrl/api/articles")
    }

    override fun getArticlesFilteredByTag(tag: String) {
        executeRequest(HttpMethod.GET, "$baseUrl/api/articles?tag=$tag")
    }

    // --- Article confirmations ---

    override fun confirmArticlePublished() {
        assertEquals(HttpStatus.OK, lastResponse!!.statusCode)
        val article = parseLastResponse<ArticleResponse>().article
        assertNotNull(article, "Expected article in response")
        assertTrue(article!!.slug.isNotEmpty(), "Article should have a slug")
    }

    override fun confirmArticleDetails(title: String, description: String, body: String) {
        val article = parseLastResponse<ArticleResponse>().article!!
        assertEquals(title, article.title)
        assertEquals(description, article.description)
        assertEquals(body, article.body)
    }

    override fun confirmArticleTags(expectedTags: List<String>) {
        val article = parseLastResponse<ArticleResponse>().article!!
        assertEquals(expectedTags.sorted(), article.tagList.sorted(), "Article tags should match")
    }

    override fun confirmArticleTitle(expectedTitle: String) {
        val article = parseLastResponse<ArticleResponse>().article!!
        assertEquals(expectedTitle, article.title)
    }

    override fun confirmArticleListMinSize(minCount: Int) {
        assertEquals(HttpStatus.OK, lastResponse!!.statusCode)
        val articles = parseLastResponse<ArticlesResponse>().articles
        assertTrue(
            articles.size >= minCount,
            "Expected at least $minCount article(s), got ${articles.size}"
        )
    }

    override fun confirmArticleListContainsTitle(title: String) {
        val articles = parseLastResponse<ArticlesResponse>().articles
        assertTrue(
            articles.any { it.title == title },
            "Expected article titled '$title' in list: ${articles.map { it.title }}"
        )
    }

    override fun confirmAllArticlesHaveTag(tag: String) {
        val articles = parseLastResponse<ArticlesResponse>().articles
        assertTrue(articles.isNotEmpty(), "Article list should not be empty")
        articles.forEach { article ->
            assertTrue(
                article.tagList.contains(tag),
                "Article '${article.title}' missing tag '$tag', has: ${article.tagList}"
            )
        }
    }

    // --- Last response metadata ---

    override fun lastArticleSlug(): String {
        val article = parseLastResponse<ArticleResponse>().article
        assertNotNull(article, "No article in last response")
        return article!!.slug
    }

    override fun lastAuthToken(): String {
        return authToken ?: error("No auth token available")
    }
}
