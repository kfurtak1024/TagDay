package dev.krfu.tagged.domain

import dev.krfu.tagged.model.ParsedTagInput

private val nameRegex = Regex("^[A-Za-z]+(?:-[A-Za-z]+)*$")
private val valueRegex = Regex("^[A-Za-z0-9]+(?:-[A-Za-z0-9]+)*$")
private val ratingRegex = Regex("^\\*{1,5}$")

object TagValidation {
    fun isValidName(name: String): Boolean = nameRegex.matches(name)

    fun parseInput(rawInput: String): Result<ParsedTagInput> {
        val input = rawInput.trim()
        if (input.isBlank()) {
            return Result.failure(IllegalArgumentException("Tag is required"))
        }

        val parts = input.split(':', limit = 2)
        val name = parts.firstOrNull().orEmpty()
        if (!isValidName(name)) {
            return Result.failure(
                IllegalArgumentException(
                    "Invalid tag name. Use letters only with single '-' separators (e.g., dinner-with-family)."
                )
            )
        }

        if (parts.size == 1) {
            return Result.success(ParsedTagInput(name = name))
        }

        val payload = parts[1]
        if (ratingRegex.matches(payload)) {
            return Result.success(
                ParsedTagInput(
                    name = name,
                    rating = payload.length
                )
            )
        }

        if (valueRegex.matches(payload)) {
            return Result.success(
                ParsedTagInput(
                    name = name,
                    value = payload
                )
            )
        }

        return Result.failure(
            IllegalArgumentException(
                "Invalid value or rating. Use words/digits with '-', or 1-5 stars."
            )
        )
    }
}
