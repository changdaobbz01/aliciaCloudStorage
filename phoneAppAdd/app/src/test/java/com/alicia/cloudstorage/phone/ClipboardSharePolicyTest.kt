package com.alicia.cloudstorage.phone

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipboardSharePolicyTest {
    private val now = 10_000_000L

    @Test
    fun ignoresClipboardContentCreatedByTheApp() {
        assertFalse(shouldPrompt(clipLabel = ALICIA_SHARE_CLIP_LABEL))
    }

    @Test
    fun ignoresTheSameClipboardRevisionIndefinitely() {
        assertFalse(shouldPrompt(lastFingerprint = "fingerprint"))
    }

    @Test
    fun ignoresTheSameShareCodeDuringCooldownEvenWhenTimestampChanges() {
        assertFalse(
            shouldPrompt(
                fingerprint = "new-fingerprint",
                lastCode = "share-code",
                lastFingerprint = "old-fingerprint",
                lastHandledAt = now - 1_000,
            ),
        )
    }

    @Test
    fun acceptsTheSameShareCodeAfterCooldown() {
        assertTrue(
            shouldPrompt(
                fingerprint = "new-fingerprint",
                lastCode = "share-code",
                lastFingerprint = "old-fingerprint",
                lastHandledAt = now - ClipboardSharePolicy.SHARE_CODE_COOLDOWN_MILLIS,
            ),
        )
    }

    @Test
    fun acceptsANewShareCodeImmediately() {
        assertTrue(shouldPrompt(lastCode = "another-share"))
    }

    private fun shouldPrompt(
        clipLabel: String? = null,
        shareCode: String = "share-code",
        fingerprint: String = "fingerprint",
        lastCode: String? = null,
        lastFingerprint: String? = null,
        lastHandledAt: Long = 0L,
    ) = ClipboardSharePolicy.shouldPrompt(
        clipLabel = clipLabel,
        shareCode = shareCode,
        fingerprint = fingerprint,
        lastHandledShareCode = lastCode,
        lastHandledFingerprint = lastFingerprint,
        lastHandledAtMillis = lastHandledAt,
        nowMillis = now,
    )
}
