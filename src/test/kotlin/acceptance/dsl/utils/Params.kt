package acceptance.dsl.utils

class Params(
    private val context: DslContext,
    private val args: Map<String, Any>,
) {
    fun optional(name: String, defaultValue: String): String =
        stringValueFor(name) ?: defaultValue

    fun optionalSequence(name: String, start: Int): String =
        stringValueFor(name) ?: context.sequenceNumberForName(name, start)

    fun optionalList(name: String, defaultValue: List<String>): List<String> {
        val value = args[name] ?: return defaultValue
        val items = toStringList(value)
        return items.ifEmpty { defaultValue }
    }

    fun alias(name: String): String {
        val value = stringValueFor(name)
            ?: throw IllegalArgumentException("No '$name' supplied for alias")
        return context.alias(value)
    }

    private fun stringValueFor(name: String): String? =
        when (val value = args[name]) {
            is String -> value.takeIf { it.isNotBlank() }
            else -> null
        }

    private fun toStringList(value: Any): List<String> =
        when (value) {
            is List<*> -> value.mapNotNull { it?.toString()?.takeIf(String::isNotBlank) }
            is String -> value.split(",").map { it.trim() }.filter { it.isNotBlank() }
            else -> emptyList()
        }
}