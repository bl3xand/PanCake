package ru.bl3xand.pancake.utils.image

import ru.bl3xand.pancake.BuildConfig

/**
 * Помощник для работы с GitHub URL изображений.
 * Преобразует между github:// схемой и полными URL с актуальным токеном.
 * Также содержит методы для работы с путями изображений.
 */
object ImageUrlHelper {

    // Constants
    private const val GITHUB_SCHEME = "github://"
    private const val RAW_GITHUB_PREFIX = "https://raw.githubusercontent.com/"
    private const val API_GITHUB_PREFIX = "https://api.github.com/"
    private const val QUERY_SEPARATOR = "?"
    private const val FRAGMENT_SEPARATOR = "#"
    private const val PATH_SEPARATOR = "/"
    private const val DEFAULT_BRANCH = "main"

    // GitHub Config
    private val owner = BuildConfig.GITHUB_OWNER.trim()
        .removePrefix("https://github.com/")
        .substringBefore("/")
    private val repo = BuildConfig.GITHUB_REPO.trim()
        .removeSuffix(".git")
        .substringAfterLast("/")
    private val branch = BuildConfig.GITHUB_BRANCH.trim().ifBlank { DEFAULT_BRANCH }

    /**
     * Определяет, является ли путь github:// схемой.
     *
     * @param path путь для проверки
     * @return true если это github:// URI
     */
    fun isGitHubScheme(path: String): Boolean =
        path.startsWith(GITHUB_SCHEME)

    /**
     * Создает github:// URI из пути в репозитории.
     * Пример: "notes/123/image.jpg" → "github://notes/123/image.jpg"
     *
     * @param repoPath путь в репозитории
     * @return github:// URI
     */
    fun createGitHubUri(repoPath: String): String =
        GITHUB_SCHEME + repoPath

    /**
     * Извлекает путь в репозитории из github:// URI.
     * Пример: "github://notes/123/image.jpg" → "notes/123/image.jpg"
     *
     * @param githubUri github:// URI
     * @return путь в репозитории или null
     */
    fun extractRepoPath(githubUri: String): String? {
        if (!isGitHubScheme(githubUri)) return null
        return githubUri.removePrefix(GITHUB_SCHEME).takeIf { it.isNotBlank() }
    }

    /**
     * Преобразует github:// URI в полный GitHub URL с актуальным токеном.
     * Пример: "github://notes/123/image.jpg" → "https://raw.githubusercontent.com/owner/repo/branch/notes/123/image.jpg"
     *
     * @param githubUri github:// URI
     * @return полный GitHub URL
     */
    fun toGitHubUrl(githubUri: String): String {
        val repoPath = extractRepoPath(githubUri) ?: return githubUri
        return "$RAW_GITHUB_PREFIX$owner$PATH_SEPARATOR$repo$PATH_SEPARATOR$branch$PATH_SEPARATOR$repoPath"
    }

    /**
     * Проверяет, является ли путь GitHub изображением.
     *
     * @param path путь для проверки
     * @return true если это github:// схема или GitHub URL
     */
    fun isGitHubImage(path: String): Boolean =
        isGitHubScheme(path) ||
        path.startsWith(RAW_GITHUB_PREFIX) ||
        path.startsWith(API_GITHUB_PREFIX)

    /**
     * Извлекает путь в репозитории из GitHub URL (формат raw.githubusercontent.com).
     *
     * @param url URL
     * @return путь в репозитории или null
     */
    fun extractRepoPathFromUrl(url: String): String? {
        if (!url.startsWith(RAW_GITHUB_PREFIX)) return null

        val withoutPrefix = url.removePrefix(RAW_GITHUB_PREFIX)
        val parts = withoutPrefix.split(PATH_SEPARATOR)
        if (parts.size <= 3) return null

        return parts.drop(3).joinToString(PATH_SEPARATOR)
            .substringBefore(QUERY_SEPARATOR)
            .substringBefore(FRAGMENT_SEPARATOR)
            .takeIf { it.isNotBlank() }
    }
}
