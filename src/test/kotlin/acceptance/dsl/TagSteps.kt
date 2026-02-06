package acceptance.dsl

import acceptance.driver.ProtocolDriver
import io.cucumber.java.en.Then
import io.cucumber.java.en.When

class TagSteps(private val driver: ProtocolDriver) {

    @When("a client requests the list of tags")
    fun requestTags() {
        driver.getTags()
    }

    @Then("the response is a list of tags")
    fun verifyTagList() {
        driver.verifyIsTagList()
    }
}
