package com.alicia.cloudstorage.phone.data

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Upsert
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.net.URI
import java.security.MessageDigest

private const val TRANSFER_DATABASE_NAME = "alicia-transfer-history.db"
private const val INTERRUPTED_MESSAGE = "应用上次退出时任务尚未完成，请重新发起传输。"
private const val MAX_TITLE_LENGTH = 255
private const val MAX_LOCATION_LENGTH = 512
private const val MAX_ERROR_LENGTH = 1_024
private const val MAX_URI_LENGTH = 2_048
private const val MAX_SOURCE_NODE_IDS = 500

internal data class StoredTransferRecord(
    val scopeKey: String,
    val taskId: Long,
    val kind: String,
    val itemKind: String,
    val title: String,
    val status: String,
    val sourceNodeIds: List<Long>,
    val sourceUri: String?,
    val destinationUri: String?,
    val transferredBytes: Long,
    val totalBytes: Long?,
    val progressPercent: Int?,
    val locationLabel: String?,
    val errorMessage: String?,
    val createdAtMillis: Long,
)

@Entity(
    tableName = "transfer_history",
    primaryKeys = ["scope_key", "task_id"],
    indices = [Index(value = ["scope_key", "created_at_millis"])],
)
internal data class TransferHistoryEntity(
    @ColumnInfo(name = "scope_key") val scopeKey: String,
    @ColumnInfo(name = "task_id") val taskId: Long,
    val kind: String,
    @ColumnInfo(name = "item_kind") val itemKind: String,
    val title: String,
    val status: String,
    @ColumnInfo(name = "source_node_ids") val sourceNodeIds: List<Long>,
    @ColumnInfo(name = "source_uri") val sourceUri: String?,
    @ColumnInfo(name = "destination_uri") val destinationUri: String?,
    @ColumnInfo(name = "transferred_bytes") val transferredBytes: Long,
    @ColumnInfo(name = "total_bytes") val totalBytes: Long?,
    @ColumnInfo(name = "progress_percent") val progressPercent: Int?,
    @ColumnInfo(name = "location_label") val locationLabel: String?,
    @ColumnInfo(name = "error_message") val errorMessage: String?,
    @ColumnInfo(name = "created_at_millis") val createdAtMillis: Long,
)

internal class TransferHistoryConverters {
    private val gson = Gson()
    private val longListType = object : TypeToken<List<Long>>() {}.type

    @TypeConverter
    fun encodeNodeIds(value: List<Long>): String = gson.toJson(value)

    @TypeConverter
    fun decodeNodeIds(value: String): List<Long> =
        runCatching { gson.fromJson<List<Long>>(value, longListType).orEmpty() }
            .getOrDefault(emptyList())
}

@Dao
internal abstract class TransferHistoryDao {
    @Query(
        "SELECT * FROM transfer_history " +
            "WHERE scope_key = :scopeKey " +
            "ORDER BY created_at_millis DESC, task_id DESC " +
            "LIMIT :limit",
    )
    abstract suspend fun load(scopeKey: String, limit: Int): List<TransferHistoryEntity>

    @Upsert
    abstract suspend fun upsert(entity: TransferHistoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertAll(entities: List<TransferHistoryEntity>)

    @Query("DELETE FROM transfer_history WHERE scope_key = :scopeKey")
    abstract suspend fun deleteScope(scopeKey: String)

    @Query(
        "DELETE FROM transfer_history " +
            "WHERE scope_key = :scopeKey AND task_id NOT IN (" +
            "SELECT task_id FROM transfer_history WHERE scope_key = :scopeKey " +
            "ORDER BY created_at_millis DESC, task_id DESC LIMIT :limit" +
            ")",
    )
    abstract suspend fun prune(scopeKey: String, limit: Int)

    @Transaction
    open suspend fun upsertAndPrune(entity: TransferHistoryEntity, limit: Int) {
        upsert(entity)
        prune(entity.scopeKey, limit)
    }

    @Transaction
    open suspend fun replaceScope(scopeKey: String, entities: List<TransferHistoryEntity>) {
        deleteScope(scopeKey)
        if (entities.isNotEmpty()) {
            insertAll(entities)
        }
    }
}

@Database(
    entities = [TransferHistoryEntity::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(TransferHistoryConverters::class)
internal abstract class TransferHistoryDatabase : RoomDatabase() {
    abstract fun transferHistoryDao(): TransferHistoryDao

    companion object {
        @Volatile
        private var instance: TransferHistoryDatabase? = null

        fun getInstance(context: Context): TransferHistoryDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    TransferHistoryDatabase::class.java,
                    TRANSFER_DATABASE_NAME,
                ).build().also { database -> instance = database }
            }
    }
}

internal interface TransferHistoryPersistence {
    suspend fun load(scopeKey: String, limit: Int): List<StoredTransferRecord>
    suspend fun upsert(record: StoredTransferRecord, limit: Int)
    suspend fun replaceScope(scopeKey: String, records: List<StoredTransferRecord>)
    suspend fun clearScope(scopeKey: String)
}

internal class TransferHistoryStore private constructor(
    private val dao: TransferHistoryDao,
) : TransferHistoryPersistence {
    override suspend fun load(scopeKey: String, limit: Int): List<StoredTransferRecord> =
        dao.load(scopeKey, limit).map(TransferHistoryEntity::toStoredRecord)

    override suspend fun upsert(record: StoredTransferRecord, limit: Int) {
        dao.upsertAndPrune(record.toEntity(), limit)
    }

    override suspend fun replaceScope(scopeKey: String, records: List<StoredTransferRecord>) {
        dao.replaceScope(scopeKey, records.map(StoredTransferRecord::toEntity))
    }

    override suspend fun clearScope(scopeKey: String) {
        dao.deleteScope(scopeKey)
    }

    companion object {
        fun create(context: Context): TransferHistoryStore =
            TransferHistoryStore(TransferHistoryDatabase.getInstance(context).transferHistoryDao())
    }
}

internal fun transferHistoryScope(baseUrl: String, userId: Long): String {
    val normalized = "${baseUrl.normalizedHistoryBaseUrl()}|$userId"
    return MessageDigest.getInstance("SHA-256")
        .digest(normalized.toByteArray(Charsets.UTF_8))
        .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
}

private fun String.normalizedHistoryBaseUrl(): String {
    val fallback = trim().trimEnd('/')
    return runCatching {
        val uri = URI(fallback)
        val scheme = uri.scheme?.lowercase() ?: return@runCatching fallback
        val host = uri.host?.lowercase() ?: return@runCatching fallback
        val port = uri.port.takeIf { it >= 0 }?.let { ":$it" }.orEmpty()
        val path = uri.rawPath.orEmpty().trimEnd('/')
        "$scheme://$host$port$path"
    }.getOrDefault(fallback)
}

internal fun StoredTransferRecord.sanitizeForPersistence(nowMillis: Long): StoredTransferRecord? =
    sanitize(nowMillis = nowMillis, interruptActiveTransfer = false)

internal fun StoredTransferRecord.sanitizeAfterRestore(nowMillis: Long): StoredTransferRecord? =
    sanitize(nowMillis = nowMillis, interruptActiveTransfer = true)

private fun StoredTransferRecord.sanitize(
    nowMillis: Long,
    interruptActiveTransfer: Boolean,
): StoredTransferRecord? {
    if (
        scopeKey.isBlank() ||
        taskId <= 0L ||
        kind !in VALID_TRANSFER_KINDS ||
        itemKind !in VALID_ITEM_KINDS ||
        status !in VALID_TRANSFER_STATUSES
    ) {
        return null
    }

    val shouldInterrupt = interruptActiveTransfer && status in ACTIVE_TRANSFER_STATUSES
    val restoredStatus = if (shouldInterrupt) "FAILED" else status
    val restoredError = if (shouldInterrupt) INTERRUPTED_MESSAGE else errorMessage
    val safeTotalBytes = totalBytes?.takeIf { it >= 0L }
    val safeProgress = when (restoredStatus) {
        "COMPLETED" -> 100
        else -> progressPercent?.coerceIn(0, 100)
    }

    return copy(
        title = title.trim().ifBlank { "未命名任务" }.take(MAX_TITLE_LENGTH),
        status = restoredStatus,
        sourceNodeIds = sourceNodeIds.asSequence().filter { it > 0L }.distinct().take(MAX_SOURCE_NODE_IDS).toList(),
        sourceUri = sourceUri.safeContentUriString(),
        destinationUri = destinationUri.safeContentUriString(),
        transferredBytes = transferredBytes.coerceAtLeast(0L),
        totalBytes = safeTotalBytes,
        progressPercent = safeProgress,
        locationLabel = locationLabel.normalizedOptionalText(MAX_LOCATION_LENGTH),
        errorMessage = restoredError.normalizedOptionalText(MAX_ERROR_LENGTH),
        createdAtMillis = createdAtMillis.takeIf { it > 0L } ?: nowMillis,
    )
}

private fun StoredTransferRecord.toEntity(): TransferHistoryEntity =
    TransferHistoryEntity(
        scopeKey = scopeKey,
        taskId = taskId,
        kind = kind,
        itemKind = itemKind,
        title = title,
        status = status,
        sourceNodeIds = sourceNodeIds,
        sourceUri = sourceUri,
        destinationUri = destinationUri,
        transferredBytes = transferredBytes,
        totalBytes = totalBytes,
        progressPercent = progressPercent,
        locationLabel = locationLabel,
        errorMessage = errorMessage,
        createdAtMillis = createdAtMillis,
    )

private fun TransferHistoryEntity.toStoredRecord(): StoredTransferRecord =
    StoredTransferRecord(
        scopeKey = scopeKey,
        taskId = taskId,
        kind = kind,
        itemKind = itemKind,
        title = title,
        status = status,
        sourceNodeIds = sourceNodeIds,
        sourceUri = sourceUri,
        destinationUri = destinationUri,
        transferredBytes = transferredBytes,
        totalBytes = totalBytes,
        progressPercent = progressPercent,
        locationLabel = locationLabel,
        errorMessage = errorMessage,
        createdAtMillis = createdAtMillis,
    )

private fun String?.safeContentUriString(): String? {
    val value = normalizedOptionalText(MAX_URI_LENGTH) ?: return null
    val uri = runCatching { URI(value) }.getOrNull() ?: return null
    return value.takeIf {
        uri.scheme.equals("content", ignoreCase = true) && !uri.rawAuthority.isNullOrBlank()
    }
}

private fun String?.normalizedOptionalText(maxLength: Int): String? =
    this?.trim()?.takeIf { it.isNotBlank() }?.take(maxLength)

private val VALID_TRANSFER_KINDS = setOf("DOWNLOAD", "UPLOAD")
private val VALID_ITEM_KINDS = setOf("FILE", "ARCHIVE")
private val VALID_TRANSFER_STATUSES = setOf("QUEUED", "PREPARING", "RUNNING", "COMPLETED", "FAILED", "CANCELED")
private val ACTIVE_TRANSFER_STATUSES = setOf("QUEUED", "PREPARING", "RUNNING")
