package com.alicia.cloudstorage.phone.ui

import com.alicia.cloudstorage.phone.data.AppTab

internal enum class TabSelectionLoad {
    HOME,
    FILES,
    TRASH,
    TEAM,
    NONE,
}

internal fun AppTab.selectionLoad(): TabSelectionLoad =
    when (this) {
        AppTab.HOME -> TabSelectionLoad.HOME
        AppTab.FILES -> TabSelectionLoad.FILES
        AppTab.TRASH -> TabSelectionLoad.TRASH
        AppTab.TEAM -> TabSelectionLoad.TEAM
        AppTab.TRANSFERS,
        AppTab.ME,
        -> TabSelectionLoad.NONE
    }
