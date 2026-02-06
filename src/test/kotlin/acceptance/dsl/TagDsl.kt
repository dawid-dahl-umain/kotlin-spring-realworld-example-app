package acceptance.dsl

import acceptance.driver.ProtocolDriver
import acceptance.dsl.utils.DslContext
import io.cucumber.java.en.Then
import io.cucumber.java.en.When

class TagDsl(
    private val context: DslContext,
    private val driver: ProtocolDriver
) {

    @When("a client requests the list of tags")
    fun requestTags() {
        driver.getTags()
    }

    @Then("the tag list includes {string}")
    fun confirmTagListContains(tag: String) {
        driver.confirmTagListContains(context.alias(tag))
    }
}