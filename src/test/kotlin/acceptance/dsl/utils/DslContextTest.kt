package acceptance.dsl.utils

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DslContextTest {

    @BeforeEach
    fun resetGlobal() {
        DslContext.resetGlobalState()
    }

    @Test
    fun `alias returns unique value`() {
        val ctx = DslContext()
        val alias = ctx.alias("user")
        assertEquals("user1", alias)
    }

    @Test
    fun `alias is idempotent within same context`() {
        val ctx = DslContext()
        val first = ctx.alias("user")
        val second = ctx.alias("user")
        assertSame(first, second)
    }

    @Test
    fun `alias increments across contexts`() {
        val ctx1 = DslContext()
        val ctx2 = DslContext()
        assertEquals("user1", ctx1.alias("user"))
        assertEquals("user2", ctx2.alias("user"))
    }

    @Test
    fun `different names get independent sequences`() {
        val ctx = DslContext()
        assertEquals("alice1", ctx.alias("alice"))
        assertEquals("bob1", ctx.alias("bob"))
    }

    @Test
    fun `workerId isolates aliases`() {
        val ctx1 = DslContext("A")
        val ctx2 = DslContext("B")
        assertEquals("user-wA-1", ctx1.alias("user"))
        assertEquals("user-wB-1", ctx2.alias("user"))
    }

    @Test
    fun `sequenceNumberForName starts at given value`() {
        val ctx = DslContext()
        assertEquals("100", ctx.sequenceNumberForName("orderId", 100))
        assertEquals("101", ctx.sequenceNumberForName("orderId", 100))
    }

    @Test
    fun `sequenceNumberForName is independent per name`() {
        val ctx = DslContext()
        assertEquals("1", ctx.sequenceNumberForName("a", 1))
        assertEquals("5", ctx.sequenceNumberForName("b", 5))
        assertEquals("2", ctx.sequenceNumberForName("a", 1))
    }

    @Test
    fun `resetGlobalState clears all sequences`() {
        val ctx1 = DslContext()
        assertEquals("x1", ctx1.alias("x"))

        DslContext.resetGlobalState()

        val ctx2 = DslContext()
        assertEquals("x1", ctx2.alias("x"))
    }
}