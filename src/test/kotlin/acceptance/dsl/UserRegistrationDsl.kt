package acceptance.dsl

import acceptance.driver.ProtocolDriver
import acceptance.dsl.utils.DslContext
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When

class UserRegistrationDsl(
    private val context: DslContext,
    private val driver: ProtocolDriver
) {

    @Given("a user already exists with username {string}, email {string}, and password {string}")
    fun createExistingUser(username: String, email: String, password: String) {
        driver.registerUser(
            context.alias(username),
            context.alias(email),
            password
        )
        driver.confirmUserRegistered()
    }

    @When("a new user registers with username {string}, email {string}, and password {string}")
    fun registerUser(username: String, email: String, password: String) {
        driver.registerUser(
            context.alias(username),
            context.alias(email),
            password
        )
    }

    @Then("the user is successfully registered")
    fun confirmUserRegistered() {
        driver.confirmUserRegistered()
    }

    @Then("registration is rejected with an error on the {string} field")
    fun confirmRegistrationError(field: String) {
        driver.confirmRegistrationError(field)
    }
}