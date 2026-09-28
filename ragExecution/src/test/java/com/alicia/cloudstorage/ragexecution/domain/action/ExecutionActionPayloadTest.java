package com.alicia.cloudstorage.ragexecution.domain.action;

import com.alicia.cloudstorage.ragexecution.domain.ExecutionActionType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExecutionActionPayloadTest {

    @Test
    void firstWaveActionsHaveStableTypedContracts() {
        assertThat(new NodeRenameAction(1L, 3L, " report.pdf ").newName()).isEqualTo("report.pdf");
        assertThat(new NodeTrashAction(2L, null).actionType()).isEqualTo(ExecutionActionType.NODE_TRASH);
        assertThat(new NodeMoveAction(3L, 4L, null).actionType()).isEqualTo(ExecutionActionType.NODE_MOVE);
        assertThat(new FolderCreateAction(null, " 项目 ").folderName()).isEqualTo("项目");
        assertThat(new ShareCreateAction(List.of(1L, 2L), null, null, 7, true, true).nodeIds())
                .containsExactly(1L, 2L);
    }

    @Test
    void rejectsInvalidIdentifiersNamesAndDuplicateShareTargets() {
        assertThatThrownBy(() -> new NodeRenameAction(0L, null, "name"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FolderCreateAction(null, " "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ShareCreateAction(List.of(1L, 1L), null, null, null, true, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicates");
        assertThatThrownBy(() -> new ShareCreateAction(List.of(1L), null, "123", null, true, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("4-32");
        assertThatThrownBy(() -> new ShareCreateAction(List.of(1L), null, null, 366, true, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1 and 365");
    }
}
