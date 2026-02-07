package acceptance.dsl

import acceptance.GlobalSetupHook
import acceptance.driver.ProtocolDriver
import acceptance.dsl.utils.DslContext
import io.cucumber.datatable.DataTable
import io.cucumber.java.en.And
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When

class ArticleDsl(
    private val context: DslContext,
    private val driver: ProtocolDriver,
) {
    private var pendingTitle: String = ""
    private var pendingDescription: String = ""
    private var pendingBody: String = ""
    private var pendingTags: List<String> = emptyList()

    @Given("a registered user is logged in")
    fun registerAndLoginUser() {
        driver.loginWithToken(GlobalSetupHook.sharedToken)
    }

    @When("the author creates an article with the following details:")
    fun setArticleDetails(dataTable: DataTable) {
        val details = dataTable.asMap(String::class.java, String::class.java)
        pendingTitle = context.alias(details["title"]!!)
        pendingDescription = details["description"]!!
        pendingBody = details["body"]!!
    }

    @And("the article is tagged with:")
    fun setArticleTagsAndPublish(dataTable: DataTable) {
        pendingTags = dataTable.asList(String::class.java).map { context.alias(it) }
        driver.createArticle(pendingTitle, pendingDescription, pendingBody, pendingTags)
    }

    @Then("the article is published successfully")
    fun confirmArticlePublished() {
        driver.confirmArticlePublished()
    }

    @And("the article details match the provided values")
    fun confirmArticleDetails() {
        driver.confirmArticleDetails(pendingTitle, pendingDescription, pendingBody)
    }

    @And("the article has the following tags:")
    fun confirmArticleTags(dataTable: DataTable) {
        val expectedTags = dataTable.asList(String::class.java).map { context.alias(it) }
        driver.confirmArticleTags(expectedTags)
    }
}
