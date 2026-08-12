package com.alicia.cloudstorage.phone.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GlobalLoadingPolicyTest {

    @Test
    fun `ai conversation owns loading feedback while it is visible`() {
        val state = AppUiState(
            isBooting = false,
            files = ExplorerUiState(
                loading = true,
                isCreatingFolder = true,
            ),
        )

        assertFalse(state.shouldShowGlobalLoading(aiChatVisible = true))
    }

    @Test
    fun `regular screens retain global loading feedback`() {
        val state = AppUiState(
            isBooting = false,
            files = ExplorerUiState(loading = true),
        )

        assertTrue(state.shouldShowGlobalLoading(aiChatVisible = false))
    }
}
