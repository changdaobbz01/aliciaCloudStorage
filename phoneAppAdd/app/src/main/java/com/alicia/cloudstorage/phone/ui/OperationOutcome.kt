package com.alicia.cloudstorage.phone.ui

internal enum class OperationOutcomeStatus {
    SUCCEEDED,
    PARTIALLY_SUCCEEDED,
    FAILED,
}

internal data class OperationOutcome(
    val status: OperationOutcomeStatus,
    val message: String,
    val affectedNodeIds: List<Long> = emptyList(),
) {
    init {
        require(message.isNotBlank()) { "Operation outcome message must not be blank." }
        require(affectedNodeIds.all { it > 0L }) { "Affected node ids must be positive." }
        require(affectedNodeIds.distinct().size == affectedNodeIds.size) {
            "Affected node ids must not contain duplicates."
        }
    }

    companion object {
        fun succeeded(message: String, affectedNodeIds: List<Long> = emptyList()) =
            OperationOutcome(OperationOutcomeStatus.SUCCEEDED, message, affectedNodeIds)

        fun partiallySucceeded(message: String, affectedNodeIds: List<Long> = emptyList()) =
            OperationOutcome(OperationOutcomeStatus.PARTIALLY_SUCCEEDED, message, affectedNodeIds)

        fun failed(message: String) = OperationOutcome(OperationOutcomeStatus.FAILED, message)
    }
}
