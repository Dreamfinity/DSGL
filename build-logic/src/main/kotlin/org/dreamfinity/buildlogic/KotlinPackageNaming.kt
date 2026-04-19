package org.dreamfinity.buildlogic

private val nonAlphanumericSeparatorRegex = Regex("[^A-Za-z0-9]+")

fun toKotlinPackageSegmentFromProjectName(projectName: String): String {
    val tokens = projectName
        .trim()
        .split(nonAlphanumericSeparatorRegex)
        .filter { it.isNotEmpty() }

    if (tokens.isEmpty()) {
        return "_"
    }

    val firstToken = tokens.first().lowercase()
    val tail = tokens.drop(1).joinToString("") { token ->
        if (token.all { it.isDigit() }) {
            token
        } else {
            token.lowercase().replaceFirstChar { ch ->
                if (ch.isLowerCase()) ch.titlecase() else ch.toString()
            }
        }
    }

    val normalized = "$firstToken$tail".ifBlank { "_" }
    return if (normalized.first().isDigit()) "_$normalized" else normalized
}
