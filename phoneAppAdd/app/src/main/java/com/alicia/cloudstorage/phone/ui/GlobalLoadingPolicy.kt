package com.alicia.cloudstorage.phone.ui

internal fun AppUiState.shouldShowGlobalLoading(aiChatVisible: Boolean = false): Boolean =
    !aiChatVisible && !isBooting && (
        isSubmittingLogin ||
            isManualRefreshing ||
            isRefreshingUser ||
            isUpdatingProfile ||
            isUpdatingAvatar ||
            isChangingPassword ||
            home.loading ||
            files.loading ||
            trash.loading ||
            team.loading ||
            files.isCreatingFolder ||
            files.actionNodeId != null ||
            files.isBatchActing ||
            files.moveTargetLoading ||
            trash.actionNodeId != null ||
            trash.isBatchActing ||
            trash.moveTargetLoading ||
            team.isCreatingUser ||
            team.quotaUserId != null ||
            team.passwordUserId != null ||
            preview.loading ||
            versionUpdate.checking ||
            incomingShare.statusLoading ||
            incomingShare.detailLoading ||
            incomingShare.passwordChecking ||
            incomingShare.saving ||
            incomingShare.saveTargetLoading
        )
