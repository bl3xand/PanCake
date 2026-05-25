package ru.bl3xand.pancake.utils.noteeditor

object NoteEditorMarkdownHelper {

    private val imageMarkdownRegex = Regex("!\\[[^\\]]*]\\(([^)]+)\\)")
    private val inlineImageRegex = Regex("(?<!\\[)!\\[([^\\]]*)]\\(([^)]+)\\)")
    private val imageOnlyListItemRegex =
        Regex("^(\\s*)(\\d+\\.|[-*+])\\s+(!\\[[^\\]]*]\\([^)]+\\))\\s*$")

    fun extractMarkdownImagePaths(markdownText: String): List<String> {
        return imageMarkdownRegex.findAll(markdownText)
            .map { it.groupValues[1].trim() }
            .filter { it.isNotBlank() }
            .toList()
    }

    fun wrapImagesWithSelfLinks(markdownText: String): String {
        return inlineImageRegex.replace(markdownText) { match ->
            val alt = match.groupValues[1]
            val path = match.groupValues[2]
            "[![$alt]($path)]($path)"
        }
    }

    fun preparePreviewMarkdown(markdownText: String): String {
        val listSafeMarkdown = markdownText
            .lineSequence()
            .flatMap { line ->
                val match = imageOnlyListItemRegex.matchEntire(line)
                if (match == null) {
                    sequenceOf(line)
                } else {
                    val indent = match.groupValues[1]
                    val marker = match.groupValues[2]
                    val imageMarkdown = match.groupValues[3]
                    val markerText = if (marker.endsWith('.')) {
                        marker.dropLast(1) + "\\."
                    } else {
                        "\\$marker"
                    }
                    sequenceOf(
                        "$indent$markerText",
                        "$indent$imageMarkdown"
                    )
                }
            }
            .joinToString("\n")

        return wrapImagesWithSelfLinks(listSafeMarkdown)
    }

    fun ensureMarkdownImages(text: String, imagePaths: List<String>, imageAltText: String): String {
        val existingMarkdownPaths = extractMarkdownImagePaths(text)
        val missingPaths = imagePaths.filter { it.isNotBlank() && it !in existingMarkdownPaths }
        if (missingPaths.isEmpty()) return text

        val appendedMarkdown = missingPaths.joinToString("\n\n") { path ->
            "![$imageAltText]($path)"
        }

        return listOf(text.trimEnd(), appendedMarkdown)
            .filter { it.isNotBlank() }
            .joinToString("\n\n")
    }

    fun removeLocalMarkdownImages(text: String): String {
        var updatedText = text
        extractMarkdownImagePaths(text)
            .filterNot { isRemotePath(it) }
            .forEach { localPath ->
                val regex = Regex("!\\[[^\\]]*]\\(${Regex.escape(localPath)}\\)\\s*")
                updatedText = updatedText.replace(regex, "")
            }

        return updatedText
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
    }

    fun isRemotePath(path: String): Boolean =
        path.startsWith("https://") || path.startsWith("http://")
}