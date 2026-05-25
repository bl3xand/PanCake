package ru.bl3xand.pancake.data.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import ru.bl3xand.pancake.data.repository.GitHubImageSyncRepository
import ru.bl3xand.pancake.data.repository.SyncQueueRepository
import ru.bl3xand.pancake.utils.logs.Logger
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Runtime-синк очереди удаления медиа.
 * Работает только пока запущен процесс приложения.
 */
object GitHubDeleteQueueSyncEngine {
    private const val TAG = "DeleteQueueSyncEngine"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val started = AtomicBoolean(false)
    private val running = AtomicBoolean(false)
    private val pendingTrigger = AtomicBoolean(false)
    private val forceRetryRequested = AtomicBoolean(false)

    private var callback: ConnectivityManager.NetworkCallback? = null

    fun start(context: Context) {
        if (!started.compareAndSet(false, true)) return

        val appContext = context.applicationContext
        val connectivity = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return

        callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trigger(appContext, forceRetryNow = true)
            }
        }

        runCatching {
            connectivity.registerDefaultNetworkCallback(callback!!)
        }.onFailure {
            Logger.logError(TAG, "register callback failed: ${it.message}")
        }

        trigger(appContext, forceRetryNow = true)
    }

    fun trigger(context: Context, forceRetryNow: Boolean = false) {
        val appContext = context.applicationContext
        if (forceRetryNow) forceRetryRequested.set(true)
        if (!running.compareAndSet(false, true)) {
            pendingTrigger.set(true)
            return
        }

        scope.launch {
            try {
                val queue = SyncQueueRepository(appContext)
                val github = GitHubImageSyncRepository(appContext)
                do {
                    pendingTrigger.set(false)
                    val forceNow = forceRetryRequested.getAndSet(false)
                    queue.processPendingDeletes(forceRetryNow = forceNow) { op ->
                        github.deleteByRepoPath(op.repoPath)
                    }
                }
                while (pendingTrigger.get())
            } catch (t: Throwable) {
                Logger.logError(TAG, "trigger failed: ${t.message}")
            } finally {
                running.set(false)
            }
        }
    }
}

