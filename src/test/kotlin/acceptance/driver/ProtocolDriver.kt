package acceptance.driver

interface ProtocolDriver {
    // Tags
    fun getTags()
    fun confirmTagListContains(tag: String)

    // User registration
    fun registerUser(username: String, email: String, password: String)
    fun confirmUserRegistered()
    fun confirmRegistrationError(field: String)

    // Authentication
    fun registerAndLogin(username: String, email: String, password: String)
    fun loginWithToken(token: String)

    // Article creation & retrieval
    fun createArticle(title: String, description: String, body: String, tags: List<String> = emptyList())
    fun getArticleBySlug(slug: String)
    fun getArticles()
    fun getArticlesFilteredByTag(tag: String)

    // Article confirmations
    fun confirmArticlePublished()
    fun confirmArticleDetails(title: String, description: String, body: String)
    fun confirmArticleTags(expectedTags: List<String>)
    fun confirmArticleTitle(expectedTitle: String)
    fun confirmArticleListMinSize(minCount: Int)
    fun confirmArticleListContainsTitle(title: String)
    fun confirmAllArticlesHaveTag(tag: String)

    // Last response metadata
    fun lastArticleSlug(): String
    fun lastAuthToken(): String
}