package com.alicia.cloudstorage.api.service;

public interface StorageQuotaAccountReader {

    StorageQuotaAccount requireAccount(Long userId);

    long getTotalAllocatedQuotaBytes();
}
