package ru.bl3xand.pancake.utils.noteeditor

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.bl3xand.pancake.data.repository.GitHubImageSyncRepository
import ru.bl3xand.pancake.data.repository.SyncQueueRepository
import ru.bl3xand.pancake.data.sync.GitHubDeleteQueueSyncEngine
import android.content.Context

class NoteEditorImageUploadHelper(
    private val context: Context,
    private val githubSync: GitHubImageSyncRepository,
    private val syncQueue: SyncQueueRepository
) {

    val currentSessionUploadedRepoUrls = mutableListOf<String>()

    suspend fun uploadImagesToGitHub(
        targetNoteId: String,
        markdownText: String
    ): UploadResult? = withContext(Dispatchers.IO) {
        var resolvedMarkdown = markdownText
        val result = mutableListOf<String>()

        NoteEditorMarkdownHelper.extractMarkdownImagePaths(markdownText)
            .distinct()
            .forEachIndexed { index, path ->
                if (NoteEditorMarkdownHelper.isRemotePath(path)) {
                    result += path
                    return@forEachIndexed
                }

                val uploadedUrl = githubSync.uploadLocalUri(targetNoteId, path, index)
                    ?: return@withContext null

                currentSessionUploadedRepoUrls += uploadedUrl
                result += uploadedUrl
                resolvedMarkdown = resolvedMarkdown.replace("($path)", "($uploadedUrl)")
            }

        UploadResult(
            markdownText = resolvedMarkdown,
            imagePaths = result
        )
    }

    suspend fun cleanupCurrentSessionUploads() {
        val copy = currentSessionUploadedRepoUrls.toList()
        if (copy.isNotEmpty()) {
            val failedRepoPaths = githubSync.deleteImages(copy)
            if (failedRepoPaths.isNotEmpty()) {
                syncQueue.enqueueDeletes(failedRepoPaths)
                GitHubDeleteQueueSyncEngine.trigger(context, forceRetryNow = true)
            }
        }
        currentSessionUploadedRepoUrls.clear()
    }

    data class UploadResult(
        val markdownText: String,
        val imagePaths: List<String>
    )
}