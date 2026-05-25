package ru.bl3xand.pancake.data.repository

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import ru.bl3xand.pancake.utils.logs.Logger
import java.io.File
import kotlin.math.min

/**
 * Надежная очередь удаления медиа из GitHub.
 * Операции переживают перезапуск приложения и ретраятся при появлении сети.
 */
class SyncQueueRepository(private val context: Context) {
    companion object {
        private const val TAG = "SyncQueueRepo"
        private const val QUEUE_DIR = "sync_queue"
        private const val DELETE_PREFIX = "delete_"
        private const val INITIAL_RETRY_DELAY_MS = 30_000L
        private const val MAX_RETRY_DELAY_MS = 6 * 60 * 60 * 1000L
    }

    private val queueDir: File
        get() = File(context.filesDir, QUEUE_DIR).also { it.mkdirs() }

    /** Добавляет удаление в очередь (дубликаты путей не создаются). */
    suspend fun enqueueDelete(repoPath: String) = withContext(Dispatchers.IO) {
        enqueueDeleteInternal(repoPath)
    }

    suspend fun enqueueDeletes(repoPaths: List<String>) = withContext(Dispatchers.IO) {
        repoPaths.distinct().forEach { enqueueDeleteInternal(it) }
    }

    /** Возвращает все операции удаления. */
    suspend fun getPendingDeletes(): List<DeleteOperation> = withContext(Dispatchers.IO) {
        listDeleteFiles().mapNotNull { parseOperation(it) }
    }

    /** Удаляет операцию из очереди после успешного выполнения */
    suspend fun removeFromQueue(queueFile: File) = withContext(Dispatchers.IO) {
        try {
            queueFile.delete()
            Logger.logDebug(TAG, "Removed from queue: ${queueFile.name}")
        } catch (t: Throwable) {
            Logger.logError(TAG, "removeFromQueue failed: ${t.message}")
        }
    }

    /**
     * Обрабатывает очередь удаления.
     * Если операция не удалась — переносится на следующую попытку с backoff.
     */
    suspend fun processPendingDeletes(
        forceRetryNow: Boolean = false,
        onDelete: suspend (DeleteOperation) -> Boolean
    ) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val deletes = getPendingDeletes()
            .filter { forceRetryNow || it.nextRetryAt <= now }

        deletes.forEach { op ->
            if (onDelete(op)) {
                removeFromQueue(op.queueFile)
                Logger.logDebug(TAG, "Processed delete: ${op.repoPath}")
            } else {
                markRetry(op)
            }
        }
    }

    suspend fun hasPendingDeletes(): Boolean = withContext(Dispatchers.IO) {
        getPendingDeletes().isNotEmpty()
    }

    data class DeleteOperation(
        val repoPath: String,
        val attempts: Int,
        val nextRetryAt: Long,
        val queueFile: File
    )

    private fun findByRepoPath(repoPath: String): DeleteOperation? {
        val files = listDeleteFiles()
        files.forEach { file ->
            val op = parseOperation(file) ?: return@forEach
            if (op.repoPath == repoPath) return op
        }
        return null
    }

    private fun enqueueDeleteInternal(repoPath: String) {
        if (repoPath.isBlank()) return
        try {
            val existing = findByRepoPath(repoPath)
            if (existing != null) return

            val data = JSONObject().apply {
                put("repoPath", repoPath)
                put("timestamp", System.currentTimeMillis())
                put("attempts", 0)
                put("nextRetryAt", 0L)
            }
            val file = File(queueDir, "$DELETE_PREFIX${System.nanoTime()}.json")
            file.writeText(data.toString())
            Logger.logDebug(TAG, "Queued delete: $repoPath")
        } catch (t: Throwable) {
            Logger.logError(TAG, "enqueueDelete failed: ${t.message}")
        }
    }

    private fun parseOperation(file: File): DeleteOperation? {
        return try {
            val json = JSONObject(file.readText())
            DeleteOperation(
                repoPath = json.getString("repoPath"),
                attempts = json.optInt("attempts", 0),
                nextRetryAt = json.optLong("nextRetryAt", 0L),
                queueFile = file
            )
        } catch (t: Throwable) {
            Logger.logError(TAG, "Failed to parse delete queue file: ${t.message}")
            null
        }
    }

    private fun listDeleteFiles(): Array<File> {
        return queueDir.listFiles { file ->
            file.name.startsWith(DELETE_PREFIX) && file.isFile
        } ?: emptyArray()
    }

    private fun markRetry(op: DeleteOperation) {
        runCatching {
            val nextAttempts = op.attempts + 1
            val delay = min(INITIAL_RETRY_DELAY_MS * (1L shl min(nextAttempts, 8)), MAX_RETRY_DELAY_MS)
            val json = JSONObject(op.queueFile.readText())
            json.put("attempts", nextAttempts)
            json.put("nextRetryAt", System.currentTimeMillis() + delay)
            op.queueFile.writeText(json.toString())
            Logger.logDebug(TAG, "Retry scheduled for ${op.repoPath}, attempts=$nextAttempts")
        }.onFailure {
            Logger.logError(TAG, "markRetry failed: ${it.message}")
        }
    }
}
