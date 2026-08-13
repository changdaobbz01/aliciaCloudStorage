package com.alicia.cloudstorage.phone.ui

import android.net.Uri
import android.util.Log
import com.alicia.cloudstorage.phone.data.StoredTransferRecord
import com.alicia.cloudstorage.phone.data.TransferHistoryPersistence
import com.alicia.cloudstorage.phone.data.sanitizeAfterRestore
import com.alicia.cloudstorage.phone.data.sanitizeForPersistence
import com.alicia.cloudstorage.phone.data.transferHistoryScope
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val DEFAULT_DEBOUNCE_MILLIS = 750L
private const val TRANSFER_LOG_TAG = "AliciaTransfers"

internal class TransferHistoryCoordinator(
    private val persistence: TransferHistoryPersistence,
    private val scope: CoroutineScope,
    private val maxHistory: Int,
    private val debounceMillis: Long = DEFAULT_DEBOUNCE_MILLIS,
    private val onPersistenceFailure: (Throwable) -> Unit = { error ->
        Log.w(TRANSFER_LOG_TAG, "Transfer history persistence failed.", error)
    },
) {
    private val latestTasks = ConcurrentHashMap<Long, TransferTask>()
    private val persistenceJobs = ConcurrentHashMap<Long, Job>()
    private val persistenceMutex = Mutex()
    @Volatile
    private var activeScopeKey: String? = null

    init {
        require(maxHistory > 0) { "maxHistory must be positive." }
        require(debounceMillis >= 0L) { "debounceMillis cannot be negative." }
    }

    suspend fun activate(baseUrl: String, userId: Long): List<TransferTask> {
        activeScopeKey = null
        cancelPendingWrites()
        latestTasks.clear()

        return persistenceMutex.withLock {
            val scopeKey = transferHistoryScope(baseUrl, userId)
            val storedRecords = persistSafely(emptyList()) {
                persistence.load(scopeKey, maxHistory)
            }
            val nowMillis = System.currentTimeMillis()
            val restoredTasks = storedRecords
                .mapNotNull { record -> record.sanitizeAfterRestore(nowMillis)?.toTransferTaskOrNull() }
                .take(maxHistory)

            activeScopeKey = scopeKey
            latestTasks.putAll(restoredTasks.associateBy(TransferTask::id))

            val canonicalRecords = restoredTasks.mapNotNull { task ->
                task.toStoredRecord(scopeKey).sanitizeForPersistence(nowMillis)
            }
            if (canonicalRecords != storedRecords) {
                persistSafely(Unit) {
                    persistence.replaceScope(scopeKey, canonicalRecords)
                }
            }
            restoredTasks
        }
    }

    fun persist(task: TransferTask, immediate: Boolean) {
        val scopeKey = activeScopeKey ?: return
        latestTasks[task.id] = task
        val pendingJob = persistenceJobs[task.id]
        if (!immediate && pendingJob?.isActive == true) {
            return
        }
        persistenceJobs.remove(task.id)?.cancel()

        val job = scope.launch {
            if (!immediate) {
                delay(debounceMillis)
            }
            persistenceMutex.withLock {
                if (scopeKey != activeScopeKey) {
                    return@withLock
                }
                val latestTask = latestTasks[task.id] ?: return@withLock
                val record = latestTask
                    .toStoredRecord(scopeKey)
                    .sanitizeForPersistence(System.currentTimeMillis())
                    ?: return@withLock
                persistSafely(Unit) {
                    persistence.upsert(
                        record = record,
                        limit = maxHistory,
                    )
                }
            }
        }
        persistenceJobs[task.id] = job
        job.invokeOnCompletion {
            persistenceJobs.remove(task.id, job)
        }
    }

    fun replace(tasks: List<TransferTask>) {
        val scopeKey = activeScopeKey ?: return
        cancelPendingWrites()
        latestTasks.clear()
        latestTasks.putAll(tasks.associateBy(TransferTask::id))

        scope.launch {
            persistenceMutex.withLock {
                if (scopeKey != activeScopeKey) {
                    return@withLock
                }
                val nowMillis = System.currentTimeMillis()
                val records = tasks.take(maxHistory).mapNotNull { task ->
                    task.toStoredRecord(scopeKey).sanitizeForPersistence(nowMillis)
                }
                persistSafely(Unit) {
                    persistence.replaceScope(scopeKey, records)
                }
            }
        }
    }

    suspend fun clearActive() {
        val scopeKey = activeScopeKey ?: return
        activeScopeKey = null
        cancelPendingWrites()
        latestTasks.clear()
        persistenceMutex.withLock {
            persistSafely(Unit) {
                persistence.clearScope(scopeKey)
            }
        }
    }

    fun close() {
        activeScopeKey = null
        cancelPendingWrites()
        latestTasks.clear()
    }

    private fun cancelPendingWrites() {
        persistenceJobs.values.forEach(Job::cancel)
        persistenceJobs.clear()
    }

    private suspend fun <T> persistSafely(fallback: T, operation: suspend () -> T): T =
        try {
            operation()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            onPersistenceFailure(error)
            fallback
        }
}

private fun TransferTask.toStoredRecord(scopeKey: String): StoredTransferRecord =
    StoredTransferRecord(
        scopeKey = scopeKey,
        taskId = id,
        kind = kind.name,
        itemKind = itemKind.name,
        title = title,
        status = status.name,
        sourceNodeIds = sourceNodeIds,
        sourceUri = sourceUri?.toString(),
        destinationUri = destinationUri?.toString(),
        transferredBytes = transferredBytes,
        totalBytes = totalBytes,
        progressPercent = progressPercent,
        locationLabel = locationLabel,
        errorMessage = errorMessage,
        createdAtMillis = createdAtMillis,
    )

private fun StoredTransferRecord.toTransferTaskOrNull(): TransferTask? {
    val transferKind = runCatching { TransferKind.valueOf(kind) }.getOrNull() ?: return null
    val transferItemKind = runCatching { TransferItemKind.valueOf(itemKind) }.getOrNull() ?: return null
    val transferStatus = runCatching { TransferStatus.valueOf(status) }.getOrNull() ?: return null

    return TransferTask(
        id = taskId,
        kind = transferKind,
        itemKind = transferItemKind,
        title = title,
        status = transferStatus,
        sourceNodeIds = sourceNodeIds,
        sourceUri = sourceUri?.let(Uri::parse),
        destinationUri = destinationUri?.let(Uri::parse),
        transferredBytes = transferredBytes,
        totalBytes = totalBytes,
        progressPercent = progressPercent,
        locationLabel = locationLabel,
        errorMessage = errorMessage,
        createdAtMillis = createdAtMillis,
    )
}
