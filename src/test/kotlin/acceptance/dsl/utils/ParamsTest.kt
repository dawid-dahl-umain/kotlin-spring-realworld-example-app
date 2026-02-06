package acceptance.dsl.utils

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ParamsTest {

    private lateinit var context: DslContext

    @BeforeEach
    fun setup() {
        DslContext.resetGlobalState()
        context = DslContext()
    }

    @Test
    fun `optional returns value when present`() {
        val params = Params(context, mapOf("name" to "Alice"))
        assertEquals("Alice", params.optional("name", "default"))
    }

    @Test
    fun `optional returns default when missing`() {
        val params = Params(context, emptyMap())
        assertEquals("default", params.optional("name", "default"))
    }

    @Test
    fun `optional returns default when blank`() {
        val params = Params(context, mapOf("name" to "  "))
        assertEquals("default", params.optional("name", "default"))
    }

    @Test
    fun `alias returns aliased value`() {
        val params = Params(context, mapOf("user" to "Alice"))
        val result = params.alias("user")
        assertEquals("Alice1", result)
    }

    @Test
    fun `alias is idempotent for same value within context`() {
        val params = Params(context, mapOf("user" to "Alice"))
        val first = params.alias("user")
        val second = params.alias("user")
        assertEquals(first, second)
    }

    @Test
    fun `alias throws when key is missing`() {
        val params = Params(context, emptyMap())
        assertThrows(IllegalArgumentException::class.java) {
            params.alias("user")
        }
    }

    @Test
    fun `optionalSequence returns value when present`() {
        val params = Params(context, mapOf("id" to "42"))
        assertEquals("42", params.optionalSequence("id", 1))
    }

    @Test
    fun `optionalSequence returns sequence when missing`() {
        val params = Params(context, emptyMap())
        assertEquals("1", params.optionalSequence("id", 1))
        assertEquals("2", params.optionalSequence("id", 1))
    }

    @Test
    fun `optionalList returns list when present as List`() {
        val params = Params(context, mapOf("tags" to listOf("a", "b")))
        assertEquals(listOf("a", "b"), params.optionalList("tags", emptyList()))
    }

    @Test
    fun `optionalList parses comma-separated string`() {
        val params = Params(context, mapOf("tags" to "a, b, c"))
        assertEquals(listOf("a", "b", "c"), params.optionalList("tags", emptyList()))
    }

    @Test
    fun `optionalList returns default when missing`() {
        val params = Params(context, emptyMap())
        assertEquals(listOf("x"), params.optionalList("tags", listOf("x")))
    }

    @Test
    fun `optionalList returns default for empty list`() {
        val params = Params(context, mapOf("tags" to emptyList<String>()))
        assertEquals(listOf("fallback"), params.optionalList("tags", listOf("fallback")))
    }

    @Test
    fun `optionalList filters blank entries from comma string`() {
        val params = Params(context, mapOf("tags" to "a, , b"))
        assertEquals(listOf("a", "b"), params.optionalList("tags", emptyList()))
    }
}
