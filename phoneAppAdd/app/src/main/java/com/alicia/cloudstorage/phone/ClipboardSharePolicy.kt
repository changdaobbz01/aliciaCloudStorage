package com.alicia.cloudstorage.phone

const val ALICIA_SHARE_CLIP_LABEL = "Alicia 云盘分享"

object ClipboardSharePolicy {
    const val SHARE_CODE_COOLDOWN_MILLIS = 6L * 60 * 60 * 1000

    fun shouldPrompt(
        clipLabel: String?,
        shareCode: String,
        fingerprint: String,
        lastHandledShareCode: String?,
        lastHandledFingerprint: String?,
        lastHandledAtMillis: Long,
        nowMillis: Long,
    ): Boolean {
        if (clipLabel == ALICIA_SHARE_CLIP_LABEL) {
            return false
        }
        if (fingerprint == lastHandledFingerprint) {
            return false
        }

        val handledAgeMillis = (nowMillis - lastHandledAtMillis).coerceAtLeast(0L)
        if (
            shareCode == lastHandledShareCode &&
            lastHandledAtMillis > 0L &&
            handledAgeMillis < SHARE_CODE_COOLDOWN_MILLIS
        ) {
            return false
        }

        return true
    }
}
