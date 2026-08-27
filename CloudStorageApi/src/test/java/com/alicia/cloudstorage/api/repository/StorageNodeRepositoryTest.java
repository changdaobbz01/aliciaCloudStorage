package com.alicia.cloudstorage.api.repository;

import com.alicia.cloudstorage.api.entity.NodeType;
import com.alicia.cloudstorage.api.entity.StorageNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(showSql = false, properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false"
})
class StorageNodeRepositoryTest {

    @Autowired
    private StorageNodeRepository storageNodeRepository;

    @Test
    void searchOperationalTrashNodesReturnsOnlyRootTrashItemsByDefaultFilterShape() {
        StorageNode deletedFolder = storageNodeRepository.saveAndFlush(trashNode(
                7L,
                null,
                "已删除目录",
                NodeType.FOLDER,
                0L,
                LocalDateTime.of(2026, 8, 27, 9, 0)
        ));
        StorageNode nestedDeletedFile = storageNodeRepository.saveAndFlush(trashNode(
                7L,
                deletedFolder.getId(),
                "目录内报告.pdf",
                NodeType.FILE,
                2048L,
                LocalDateTime.of(2026, 8, 27, 9, 1)
        ));
        StorageNode rootDeletedFile = storageNodeRepository.saveAndFlush(trashNode(
                8L,
                null,
                "根层报告.pdf",
                NodeType.FILE,
                4096L,
                LocalDateTime.of(2026, 8, 27, 9, 2)
        ));
        storageNodeRepository.saveAndFlush(activeNode(
                7L,
                null,
                "未删除报告.pdf",
                NodeType.FILE,
                512L
        ));

        var page = storageNodeRepository.searchOperationalTrashNodes(
                null,
                null,
                null,
                true,
                PageRequest.of(0, 1, trashSort())
        );

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).extracting(StorageNode::getId)
                .containsExactly(rootDeletedFile.getId());
        assertThat(page.getContent()).extracting(StorageNode::getId)
                .doesNotContain(nestedDeletedFile.getId());
    }

    @Test
    void searchOperationalTrashNodesCanIncludeNestedTrashItemsAndFilterOwnerKeywordType() {
        StorageNode ownerFolder = storageNodeRepository.saveAndFlush(trashNode(
                7L,
                null,
                "项目目录",
                NodeType.FOLDER,
                0L,
                LocalDateTime.of(2026, 8, 27, 10, 0)
        ));
        StorageNode ownerReport = storageNodeRepository.saveAndFlush(trashNode(
                7L,
                ownerFolder.getId(),
                "项目报告.pdf",
                NodeType.FILE,
                1024L,
                LocalDateTime.of(2026, 8, 27, 10, 1)
        ));
        storageNodeRepository.saveAndFlush(trashNode(
                8L,
                null,
                "项目报告.pdf",
                NodeType.FILE,
                2048L,
                LocalDateTime.of(2026, 8, 27, 10, 2)
        ));

        var page = storageNodeRepository.searchOperationalTrashNodes(
                7L,
                "报告",
                NodeType.FILE,
                false,
                PageRequest.of(0, 10, trashSort())
        );

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent()).singleElement().satisfies(node -> {
            assertThat(node.getId()).isEqualTo(ownerReport.getId());
            assertThat(node.getParentId()).isEqualTo(ownerFolder.getId());
            assertThat(node.getNodeType()).isEqualTo(NodeType.FILE);
        });
    }

    private StorageNode trashNode(
            Long ownerId,
            Long parentId,
            String name,
            NodeType nodeType,
            Long size,
            LocalDateTime deletedAt
    ) {
        StorageNode node = activeNode(ownerId, parentId, name, nodeType, size);
        node.setDeleted(true);
        node.setDeletedBy(ownerId);
        node.setDeletedAt(deletedAt);
        node.setOriginalParentId(parentId);
        return node;
    }

    private StorageNode activeNode(Long ownerId, Long parentId, String name, NodeType nodeType, Long size) {
        StorageNode node = new StorageNode();
        node.setOwnerId(ownerId);
        node.setParentId(parentId);
        node.setNodeName(name);
        node.setNodeType(nodeType);
        node.setFileSize(size);
        return node;
    }

    private Sort trashSort() {
        return Sort.by(
                new Sort.Order(Sort.Direction.DESC, "deletedAt").nullsLast(),
                new Sort.Order(Sort.Direction.ASC, "id")
        );
    }
}
