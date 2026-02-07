package acceptance.dsl

import acceptance.driver.ProtocolDriver
import acceptance.dsl.utils.DslContext
import io.cucumber.java.en.And
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When

class ArticleManagementDsl(
    private val context: DslContext,
    private val driver: ProtocolDriver,
) {
    private var lastPublishedSlug: String = ""

    @Given("the author has published an article titled {string}")
    fun publishArticleWithTitle(title: String) {
        val aliasedTitle = context.alias(title)
        driver.createArticle(aliasedTitle, "Description for $aliasedTitle", "Body content for $aliasedTitle")
        lastPublishedSlug = driver.lastArticleSlug()
    }

    @Given("the author has published an article titled {string} with tag {string}")
    fun publishArticleWithTitleAndTag(
        title: String,
        tag: String,
    ) {
        val aliasedTitle = context.alias(title)
        val aliasedTag = context.alias(tag)
        driver.createArticle(aliasedTitle, "Description for $aliasedTitle", "Body content for $aliasedTitle", listOf(aliasedTag))
        lastPublishedSlug = driver.lastArticleSlug()
    }

    @When("the author retrieves the article")
    fun retrieveArticle() {
        driver.getArticleBySlug(lastPublishedSlug)
    }

    @Then("the article title is {string}")
    fun confirmArticleTitle(expectedTitle: String) {
        driver.confirmArticleTitle(context.alias(expectedTitle))
    }

    @When("a client requests the list of articles")
    fun requestArticles() {
        driver.getArticles()
    }

    @When("a client requests articles filtered by tag {string}")
    fun requestArticlesFilteredByTag(tag: String) {
        driver.getArticlesFilteredByTag(context.alias(tag))
    }

    @Then("the article list contains at least {int} article(s)")
    fun confirmArticleListMinSize(minCount: Int) {
        driver.confirmArticleListMinSize(minCount)
    }

    @Then("the article list contains an article titled {string}")
    fun confirmArticleListContainsTitle(title: String) {
        driver.confirmArticleListContainsTitle(context.alias(title))
    }

    @And("every article in the list is tagged with {string}")
    fun confirmAllArticlesHaveTag(tag: String) {
        driver.confirmAllArticlesHaveTag(context.alias(tag))
    }
}
