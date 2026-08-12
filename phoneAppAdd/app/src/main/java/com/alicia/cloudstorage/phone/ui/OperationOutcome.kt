package com.alicia.cloudstorage.phone.ui

internal enum class OperationOutcomeStatus {
    SUCCEEDED,
    PARTIALLY_SUCCEEDED,
    FAILED,
}

internal data class OperationOutcome(
    val status: OperationOutcomeStatus,
    val message: String,
) {
    init {
        require(message.isNotBlank()) { "Operation outcome message must not be blank." }
    }

    companion object {
        fun succeeded(message: String) = OperationOutcome(OperationOutcomeStatus.SUCCEEDED, message)

        fun partiallySucceeded(message: String) =
            OperationOutcome(OperationOutcomeStatus.PARTIALLY_SUCCEEDED, message)

        fun failed(message: String) = OperationOutcome(OperationOutcomeStatus.FAILED, message)
    }
}
