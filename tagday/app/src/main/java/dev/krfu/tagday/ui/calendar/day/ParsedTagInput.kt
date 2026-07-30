package dev.krfu.tagday.ui.calendar.day

sealed interface ParsedTagInput {
    val name: String

    data class Ambiguous(override val name: String) : ParsedTagInput
    data class Rated(override val name: String, val rating: Int) : ParsedTagInput

    /**
     * [values] is never empty. A comma-separated suffix seeds one instance per value, so
     * `film:dune,tenet` creates the tag with two values rather than one literal
     * `"dune,tenet"` — commas aren't much use inside a single value, and typing a few at
     * once is the common case.
     */
    data class Valued(override val name: String, val values: List<String>) : ParsedTagInput

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
                else -> {
                    val values = suffix.split(',').map { it.trim() }.filter { it.isNotEmpty() }
                    // All-comma suffixes ("film:,,,") have nothing to seed, so they're as
                    // ambiguous as a bare name.
                    if (values.isEmpty()) Ambiguous(name) else Valued(name, values)
                }
            }
        }
    }
}
