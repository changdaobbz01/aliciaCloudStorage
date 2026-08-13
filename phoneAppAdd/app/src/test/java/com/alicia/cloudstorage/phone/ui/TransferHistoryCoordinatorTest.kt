package com.alicia.cloudstorage.phone.ui

import com.alicia.cloudstorage.phone.data.StoredTransferRecord
import com.alicia.cloudstorage.phone.data.TransferHistoryPersistence
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransferHistoryCoordinatorTest {
    @Test
    fun `activation converts interrupted work before exposing it`() = runBlocking {
        val persistence = FakeTransferHistoryPersistence(
            initial = listOf(record(status = "RUNNING", transferredBytes = 10L)),
        )
        val coordinator = coordinator(persistence)

        val restored = coordinator.activate("https://example.com", 7L)

        assertEquals(TransferStatus.FAILED, restored.single().status)
        assertTrue(restored.single().errorMessage.orEmpty().contains("应用上次退出"))
        assertEquals("FAILED", persistence.replaced.single().status)
        coordinator.close()
    }

    @Test
    fun `progress writes are coalesced and keep the newest task`() = runBlocking {
        val persistence = FakeTransferHistoryPersistence()
        val testScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val coordinator = TransferHistoryCoordinator(
            persistence = persistence,
            scope = testScope,
            maxHistory = 600,
            debounceMillis = 20L,
            onPersistenceFailure = { throw AssertionError(it) },
        )
        coordinator.activate("https://example.com", 7L)

        coordinator.persist(task(status = TransferStatus.RUNNING, transferredBytes = 10L), immediate = false)
        coordinator.persist(task(status = TransferStatus.RUNNING, transferredBytes = 20L), immediate = false)
        coordinator.persist(task(status = TransferStatus.RUNNING, transferredBytes = 30L), immediate = false)
        delay(100L)

        assertEquals(1, persistence.upserts.size)
        assertEquals(30L, persistence.upserts.single().transferredBytes)
        coordinator.close()
        testScope.cancel()
    }

    @Test
    fun `terminal state preempts a delayed progress write`() = runBlocking {
        val persistence = FakeTransferHistoryPersistence()
        val testScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val coordinator = TransferHistoryCoordinator(
            persistence = persistence,
            scope = testScope,
            maxHistory = 600,
            debounceMillis = 1_000L,
            onPersistenceFailure = { throw AssertionError(it) },
        )
        coordinator.activate("https://example.com", 7L)

        coordinator.persist(task(status = TransferStatus.RUNNING, transferredBytes = 30L), immediate = false)
        coordinator.persist(task(status = TransferStatus.COMPLETED, transferredBytes = 100L), immediate = true)
        delay(100L)

        assertEquals(1, persistence.upserts.size)
        assertEquals("COMPLETED", persistence.upserts.single().status)
        coordinator.close()
        testScope.cancel()
    }

    @Test
    fun `scope activation waits for an in-flight database write`() = runBlocking {
        val persistence = BlockingTransferHistoryPersistence()
        val testScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val coordinator = TransferHistoryCoordinator(
            persistence = persistence,
            scope = testScope,
            maxHistory = 600,
            debounceMillis = 0L,
            onPersistenceFailure = { throw AssertionError(it) },
        )
        coordinator.activate("https://example.com", 7L)

        coordinator.persist(task(status = TransferStatus.RUNNING, transferredBytes = 10L), immediate = true)
        persistence.writeStarted.await()
        val activation = async { coordinator.activate("https://example.com", 8L) }
        delay(50L)

        assertFalse(persistence.secondScopeLoaded.isCompleted)
        persistence.allowWrite.complete(Unit)
        activation.await()
        assertTrue(persistence.secondScopeLoaded.isCompleted)
        coordinator.close()
        testScope.cancel()
    }

    private fun coordinator(persistence: TransferHistoryPersistence): TransferHistoryCoordinator =
        TransferHistoryCoordinator(
            persistence = persistence,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            maxHistory = 600,
            debounceMillis = 1L,
            onPersistenceFailure = { throw AssertionError(it) },
        )

    private fun task(status: TransferStatus, transferredBytes: Long): TransferTask =
        TransferTask(
            id = 1L,
            kind = TransferKind.DOWNLOAD,
            itemKind = TransferItemKind.FILE,
            title = "report.pdf",
            status = status,
            sourceNodeIds = listOf(9L),
            transferredBytes = transferredBytes,
            totalBytes = 100L,
            progressPercent = transferredBytes.toInt(),
            createdAtMillis = 1L,
        )

    private fun record(status: String, transferredBytes: Long): StoredTransferRecord =
        StoredTransferRecord(
            scopeKey = "scope",
            taskId = 1L,
            kind = "DOWNLOAD",
            itemKind = "FILE",
            title = "report.pdf",
            status = status,
            sourceNodeIds = listOf(9L),
            sourceUri = null,
            destinationUri = null,
            transferredBytes = transferredBytes,
            totalBytes = 100L,
            progressPercent = transferredBytes.toInt(),
            locationLabel = null,
            errorMessage = null,
            createdAtMillis = 1L,
        )
}

private class BlockingTransferHistoryPersistence : TransferHistoryPersistence {
    val writeStarted = CompletableDeferred<Unit>()
    val allowWrite = CompletableDeferred<Unit>()
    val secondScopeLoaded = CompletableDeferred<Unit>()
    private var loadCount = 0

    override suspend fun load(scopeKey: String, limit: Int): List<StoredTransferRecord> {
        loadCount += 1
        if (loadCount == 2) {
            secondScopeLoaded.complete(Unit)
        }
        return emptyList()
    }

    override suspend fun upsert(record: StoredTransferRecord, limit: Int) {
        writeStarted.complete(Unit)
        withContext(NonCancellable) {
            allowWrite.await()
        }
    }

    override suspend fun replaceScope(scopeKey: String, records: List<StoredTransferRecord>) = Unit

    override suspend fun clearScope(scopeKey: String) = Unit

}

private class FakeTransferHistoryPersistence(
    private val initial: List<StoredTransferRecord> = emptyList(),
) : TransferHistoryPersistence {
    val upserts = CopyOnWriteArrayList<StoredTransferRecord>()
    var replaced: List<StoredTransferRecord> = emptyList()

    override suspend fun load(scopeKey: String, limit: Int): List<StoredTransferRecord> = initial.take(limit)

    override suspend fun upsert(record: StoredTransferRecord, limit: Int) {
        upserts += record
    }

    override suspend fun replaceScope(scopeKey: String, records: List<StoredTransferRecord>) {
        replaced = records
    }

    override suspend fun clearScope(scopeKey: String) = Unit

}
