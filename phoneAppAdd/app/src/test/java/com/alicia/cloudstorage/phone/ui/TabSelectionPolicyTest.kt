package com.alicia.cloudstorage.phone.ui

import com.alicia.cloudstorage.phone.data.AppTab
import org.junit.Assert.assertEquals
import org.junit.Test

class TabSelectionPolicyTest {
    @Test
    fun `switching to me never requests an automatic reload`() {
        assertEquals(TabSelectionLoad.NONE, AppTab.ME.selectionLoad())
    }

    @Test
    fun `data tabs retain their lazy initial load`() {
        assertEquals(TabSelectionLoad.HOME, AppTab.HOME.selectionLoad())
        assertEquals(TabSelectionLoad.FILES, AppTab.FILES.selectionLoad())
        assertEquals(TabSelectionLoad.TRASH, AppTab.TRASH.selectionLoad())
        assertEquals(TabSelectionLoad.TEAM, AppTab.TEAM.selectionLoad())
    }
}
