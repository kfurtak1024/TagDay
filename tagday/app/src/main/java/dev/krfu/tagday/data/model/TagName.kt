package dev.krfu.tagday.data.model

/**
 * The shape a tag name is allowed to take: lowercase letters, single `-` separators, and
 * nothing else — `walk`, `fast-food`, `playing-game`. Shared by every entry point that can
 * name a tag (the quick-entry bar and the rename dialog) so the rule is stated once.
 *
 * Two halves, because typing needs to be permissive while saving must not be:
 * [sanitize] runs on every keystroke and drops what can never be part of a name, while
 * [isValid] gates the actual save. They differ deliberately over a trailing `-`: sanitize
 * has to keep it (you can't type `fast-food` without passing through `fast-`), and isValid
 * has to reject it.
 */
object TagName {
    private val VALID = Regex("^[a-z]+(-[a-z]+)*$")

    /**
     * Normalizes as-you-type input: lowercases, rewrites anything separator-shaped
     * (whitespace, `_`, `-`) to a single `-`, drops everything else, and never allows a
     * leading or doubled separator. The result may still be incomplete (empty, or ending in
     * `-`) — that's what [isValid] is for.
     *
     * Whitespace becoming `-` rather than being dropped is deliberate: `-` is *the* separator
     * for this project's names (`fast-food`, `playing-game`), so someone typing two words
     * means a separator between them, not one word run together.
     */
    fun sanitize(raw: String): String {
        val builder = StringBuilder(raw.length)
        for (char in raw.lowercase()) {
            when {
                char in 'a'..'z' -> builder.append(char)
                // A separator needs something before it, and never doubles up.
                char.isSeparator() && builder.isNotEmpty() && builder.last() != '-' ->
                    builder.append('-')
            }
        }
        return builder.toString()
    }

    private fun Char.isSeparator(): Boolean = this == '-' || this == '_' || isWhitespace()

    /** Whether [name] is savable: starts and ends with a letter, single separators between. */
    fun isValid(name: String): Boolean = VALID.matches(name)
}
