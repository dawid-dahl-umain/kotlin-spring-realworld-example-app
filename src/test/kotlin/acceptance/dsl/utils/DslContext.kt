package acceptance.dsl.utils

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class DslContext(
    private val workerId: String? = null,
) {
    private val localAliases = mutableMapOf<String, String>()
    private val localSequences = mutableMapOf<String, AtomicInteger>()

    fun alias(name: String): String = localAliases.getOrPut(name) { mintAlias(name) }

    fun sequenceNumberForName(
        name: String,
        start: Int,
    ): String {
        val counter = localSequences.getOrPut(name) { AtomicInteger(start) }
        return counter.getAndIncrement().toString()
    }

    private fun mintAlias(name: String): String {
        val key = if (workerId != null) "$name-w$workerId" else name
        val sequence = globalSequenceFor(key).incrementAndGet()
        return if (workerId != null) {
            "$name-w$workerId-$sequence"
        } else {
            "$name$sequence"
        }
    }

    companion object {
        private val globalSequences = ConcurrentHashMap<String, AtomicInteger>()

        private fun globalSequenceFor(name: String): AtomicInteger = globalSequences.getOrPut(name) { AtomicInteger(0) }

        internal fun resetGlobalState() {
            globalSequences.clear()
        }
    }
}
