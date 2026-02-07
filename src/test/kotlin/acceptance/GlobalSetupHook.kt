package acceptance

import acceptance.driver.ProtocolDriver
import io.cucumber.java.Before

class GlobalSetupHook(
    private val driver: ProtocolDriver,
) {
    @Before(order = 0)
    fun registerSharedUser() {
        if (initialized) return

        driver.registerAndLogin(SHARED_USERNAME, SHARED_EMAIL, SHARED_PASSWORD)
        sharedToken = driver.lastAuthToken()
        initialized = true
    }

    companion object {
        private const val SHARED_USERNAME = "shared-test-user"
        private const val SHARED_EMAIL = "shared-test-user@test.com"
        private const val SHARED_PASSWORD = "password123"

        @Volatile
        var initialized = false
            private set

        @Volatile
        var sharedToken: String = ""
            private set
    }
}
