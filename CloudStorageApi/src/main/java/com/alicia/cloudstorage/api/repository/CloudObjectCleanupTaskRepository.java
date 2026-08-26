package com.alicia.cloudstorage.api.repository;

import com.alicia.cloudstorage.api.entity.CloudObjectCleanupStatus;
import com.alicia.cloudstorage.api.entity.CloudObjectCleanupTask;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface CloudObjectCleanupTaskRepository extends JpaRepository<CloudObjectCleanupTask, Long> {

    @Query("""
            select task.id
            from CloudObjectCleanupTask task
            where task.status in :statuses
              and task.nextRetryAt <= :now
            order by task.createdAt asc, task.id asc
            """)
    List<Long> findDueTaskIds(
            @Param("statuses") Collection<CloudObjectCleanupStatus> statuses,
            @Param("now") LocalDateTime now,
            Pageable pageable
    );

    long countByStatus(CloudObjectCleanupStatus status);
}
