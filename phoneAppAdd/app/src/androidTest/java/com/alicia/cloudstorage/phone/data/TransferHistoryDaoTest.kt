package com.alicia.cloudstorage.phone.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TransferHistoryDaoTest {
    private lateinit var database: TransferHistoryDatabase
    private lateinit var dao: TransferHistoryDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            TransferHistoryDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = database.transferHistoryDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun recordsAreScopedAndPruned() = runBlocking {
        dao.upsert(entity("account-a", 1L, 1L))
        dao.upsertAndPrune(entity("account-a", 2L, 2L), limit = 1)
        dao.upsert(entity("account-b", 3L, 3L))

        assertEquals(listOf(2L), dao.load("account-a", 10).map { it.taskId })
        assertEquals(listOf(3L), dao.load("account-b", 10).map { it.taskId })
    }

    @Test
    fun replaceScopeDoesNotTouchAnotherAccount() = runBlocking {
        dao.upsert(entity("account-a", 1L, 1L))
        dao.upsert(entity("account-b", 2L, 2L))
        dao.replaceScope("account-a", listOf(entity("account-a", 3L, 3L)))

        assertEquals(listOf(3L), dao.load("account-a", 10).map { it.taskId })
        assertEquals(listOf(2L), dao.load("account-b", 10).map { it.taskId })
    }

    private fun entity(scope: String, taskId: Long, createdAt: Long): TransferHistoryEntity =
        TransferHistoryEntity(
            scopeKey = scope,
            taskId = taskId,
            kind = "DOWNLOAD",
            itemKind = "FILE",
            title = "file-$taskId",
            status = "COMPLETED",
            sourceNodeIds = listOf(taskId),
            sourceUri = null,
            destinationUri = null,
            transferredBytes = 10L,
            totalBytes = 10L,
            progressPercent = 100,
            locationLabel = null,
            errorMessage = null,
            createdAtMillis = createdAt,
        )
}
