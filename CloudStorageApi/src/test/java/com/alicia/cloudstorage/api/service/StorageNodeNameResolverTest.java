package com.alicia.cloudstorage.api.service;

import com.alicia.cloudstorage.api.repository.StorageNodeRepository;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StorageNodeNameResolverTest {

    @Test
    void resolveAvailableSiblingNameSkipsReservedNamesCaseInsensitively() {
        StorageNodeRepository storageNodeRepository = mock(StorageNodeRepository.class);
        Set<String> reservedNames = new HashSet<>(Set.of("REPORT.PDF"));
        when(storageNodeRepository.existsActiveSiblingName(7L, 3L, "Report (1).pdf")).thenReturn(false);

        String resolvedName = StorageNodeNameResolver.resolveAvailableSiblingName(
                storageNodeRepository,
                7L,
                3L,
                "Report.pdf",
                reservedNames
        );

        assertThat(resolvedName).isEqualTo("Report (1).pdf");
        assertThat(reservedNames).contains("report.pdf", "report (1).pdf");
        verify(storageNodeRepository, never()).existsActiveSiblingName(7L, 3L, "Report.pdf");
    }
}
