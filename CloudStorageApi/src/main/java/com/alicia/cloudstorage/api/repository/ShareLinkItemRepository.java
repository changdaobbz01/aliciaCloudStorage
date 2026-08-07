package com.alicia.cloudstorage.api.repository;

import com.alicia.cloudstorage.api.entity.ShareLinkItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ShareLinkItemRepository extends JpaRepository<ShareLinkItem, Long> {

    List<ShareLinkItem> findByShareIdOrderBySortOrderAsc(Long shareId);

    List<ShareLinkItem> findByShareIdIn(Collection<Long> shareIds);

    long countByShareId(Long shareId);
}
