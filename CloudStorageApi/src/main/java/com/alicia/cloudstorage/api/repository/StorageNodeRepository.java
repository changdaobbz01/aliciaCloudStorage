package com.alicia.cloudstorage.api.repository;

import com.alicia.cloudstorage.api.entity.NodeType;
import com.alicia.cloudstorage.api.entity.StorageNode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface StorageNodeRepository extends JpaRepository<StorageNode, Long> {

    Optional<StorageNode> findByIdAndOwnerId(Long id, Long ownerId);

    Optional<StorageNode> findByIdAndOwnerIdAndDeletedFalse(Long id, Long ownerId);

    Optional<StorageNode> findByIdAndOwnerIdAndDeletedTrue(Long id, Long ownerId);

    List<StorageNode> findByOwnerIdAndIdInAndDeletedFalse(Long ownerId, Collection<Long> ids);

    List<StorageNode> findByOwnerIdAndIdInAndDeletedTrue(Long ownerId, Collection<Long> ids);

    List<StorageNode> findByOwnerIdAndParentId(Long ownerId, Long parentId);

    List<StorageNode> findByOwnerIdAndParentIdAndDeletedFalse(Long ownerId, Long parentId);

    @Query("""
            select node
            from StorageNode node
            where node.ownerId = :ownerId
              and node.deleted = false
              and node.nodeType = com.alicia.cloudstorage.api.entity.NodeType.FOLDER
            """)
    List<StorageNode> findActiveFolders(@Param("ownerId") Long ownerId);

    @Query("""
            select node
            from StorageNode node
            where node.ownerId = :ownerId
              and node.deleted = false
              and ((:parentId is null and node.parentId is null) or node.parentId = :parentId)
              and (:keyword is null or lower(node.nodeName) like lower(concat('%', :keyword, '%')))
              and (:nodeType is null or node.nodeType = :nodeType)
            """)
    Page<StorageNode> searchNodes(
            @Param("ownerId") Long ownerId,
            @Param("parentId") Long parentId,
            @Param("keyword") String keyword,
            @Param("nodeType") NodeType nodeType,
            Pageable pageable
    );

    @Query("""
            select (count(node) > 0)
            from StorageNode node
            where node.ownerId = :ownerId
              and node.deleted = false
              and node.nodeName = :nodeName
              and ((:parentId is null and node.parentId is null) or node.parentId = :parentId)
            """)
    boolean existsActiveSiblingName(
            @Param("ownerId") Long ownerId,
            @Param("parentId") Long parentId,
            @Param("nodeName") String nodeName
    );

    @Query("""
            select (count(node) > 0)
            from StorageNode node
            where node.ownerId = :ownerId
              and node.deleted = false
              and node.id <> :excludedId
              and node.nodeName = :nodeName
              and ((:parentId is null and node.parentId is null) or node.parentId = :parentId)
            """)
    boolean existsActiveSiblingNameExcludingId(
            @Param("ownerId") Long ownerId,
            @Param("parentId") Long parentId,
            @Param("nodeName") String nodeName,
            @Param("excludedId") Long excludedId
    );

    @Query("""
            select node
            from StorageNode node
            where node.ownerId = :ownerId
              and node.deleted = true
              and not exists (
                  select parent.id
                  from StorageNode parent
                  where parent.id = node.parentId
                    and parent.ownerId = :ownerId
                    and parent.deleted = true
              )
              and (:keyword is null or lower(node.nodeName) like lower(concat('%', :keyword, '%')))
              and (:nodeType is null or node.nodeType = :nodeType)
            """)
    Page<StorageNode> searchTrashNodes(
            @Param("ownerId") Long ownerId,
            @Param("keyword") String keyword,
            @Param("nodeType") NodeType nodeType,
            Pageable pageable
    );

    long countByOwnerIdAndDeletedFalse(Long ownerId);

    long countByOwnerIdAndNodeTypeAndDeletedFalse(Long ownerId, NodeType nodeType);

    long countByDeletedFalse();

    long countByNodeTypeAndDeletedFalse(NodeType nodeType);

    @Query("""
            select coalesce(sum(node.fileSize), 0)
            from StorageNode node
            where node.ownerId = :ownerId
              and node.nodeType = com.alicia.cloudstorage.api.entity.NodeType.FILE
              and node.deleted = false
            """)
    Long sumFileSizeByOwnerId(@Param("ownerId") Long ownerId);

    @Query("""
            select coalesce(sum(node.fileSize), 0)
            from StorageNode node
            where node.nodeType = com.alicia.cloudstorage.api.entity.NodeType.FILE
              and node.deleted = false
            """)
    Long sumFileSizeAllOwners();

    @Query("""
            select coalesce(sum(node.fileSize), 0)
            from StorageNode node
            where node.ownerId = :ownerId
              and node.nodeType = com.alicia.cloudstorage.api.entity.NodeType.FILE
              and node.createdAt <= :endOfDay
              and (
                  node.deleted = false
                  or node.deletedAt is null
                  or node.deletedAt > :endOfDay
              )
            """)
    Long sumActiveFileSizeByOwnerIdAt(
            @Param("ownerId") Long ownerId,
            @Param("endOfDay") LocalDateTime endOfDay
    );

    @Query("""
            select coalesce(sum(node.fileSize), 0)
            from StorageNode node
            where node.nodeType = com.alicia.cloudstorage.api.entity.NodeType.FILE
              and node.createdAt <= :endOfDay
              and (
                  node.deleted = false
                  or node.deletedAt is null
                  or node.deletedAt > :endOfDay
              )
            """)
    Long sumActiveFileSizeAllOwnersAt(@Param("endOfDay") LocalDateTime endOfDay);
}
