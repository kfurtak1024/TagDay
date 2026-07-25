package dev.krfu.tagday.ui.calendar.day

sealed interface ParsedTagInput {
    val name: String

    data class Ambiguous(override val name: String) : ParsedTagInput
    data class Rated(override val name: String, val rating: Int) : ParsedTagInput
    data class Valued(override val name: String, val value: String) : ParsedTagInput

    companion object {
        fun parse(raw: String): ParsedTagInput? {
            val separatorIndex = raw.indexOf(':')
            if (separatorIndex < 0) {
                val name = raw.trim()
                return name.ifBlank { null }?.let { Ambiguous(it) }
            }

            val name = raw.substring(0, separatorIndex).trim()
            if (name.isBlank()) return null

            val suffix = raw.substring(separatorIndex + 1).trim()
            return when {
                suffix.isBlank() -> Ambiguous(name)
                suffix.all { it == '*' } -> Rated(name, suffix.length.coerceAtMost(5))
                else -> Valued(name, suffix)
            }
        }
    }
}
