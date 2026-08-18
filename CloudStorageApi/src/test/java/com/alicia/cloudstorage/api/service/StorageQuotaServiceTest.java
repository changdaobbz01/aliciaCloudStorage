package com.alicia.cloudstorage.api.service;

import com.alicia.cloudstorage.api.entity.UserRole;
import com.alicia.cloudstorage.api.repository.StorageNodeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StorageQuotaServiceTest {

    @Mock
    private StorageQuotaAccountReader storageQuotaAccountReader;

    @Mock
    private StorageNodeRepository storageNodeRepository;

    private StorageQuotaService storageQuotaService;

    @BeforeEach
    void setUp() {
        storageQuotaService = new StorageQuotaService(
                storageQuotaAccountReader,
                storageNodeRepository,
                10_240L,
                2_048L
        );
    }

    @Test
    void isAdminReadsRoleThroughQuotaAccountReader() {
        when(storageQuotaAccountReader.requireAccount(7L))
                .thenReturn(new StorageQuotaAccount(7L, UserRole.ADMIN, 2_048L));

        assertThat(storageQuotaService.isAdmin(7L)).isTrue();
    }

    @Test
    void getUserQuotaBytesUsesDefaultWhenAccountQuotaIsMissing() {
        when(storageQuotaAccountReader.requireAccount(7L))
                .thenReturn(new StorageQuotaAccount(7L, UserRole.USER, null));

        assertThat(storageQuotaService.getUserQuotaBytes(7L)).isEqualTo(2_048L);
    }

    @Test
    void validateUploadFitsRejectsWhenRemainingSpaceIsInsufficient() {
        when(storageQuotaAccountReader.requireAccount(7L))
                .thenReturn(new StorageQuotaAccount(7L, UserRole.USER, 4_096L));
        when(storageNodeRepository.sumFileSizeByOwnerId(7L)).thenReturn(3_584L);

        assertThatThrownBy(() -> storageQuotaService.validateUploadFits(7L, 1_024L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("剩余空间不足");
    }

    @Test
    void getTotalAllocatedQuotaBytesDelegatesToQuotaAccountReader() {
        when(storageQuotaAccountReader.getTotalAllocatedQuotaBytes()).thenReturn(8_192L);

        assertThat(storageQuotaService.getTotalAllocatedQuotaBytes()).isEqualTo(8_192L);
        verify(storageQuotaAccountReader).getTotalAllocatedQuotaBytes();
    }
}
