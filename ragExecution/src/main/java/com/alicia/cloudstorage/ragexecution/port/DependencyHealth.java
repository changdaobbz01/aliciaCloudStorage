package com.alicia.cloudstorage.ragexecution.port;

public record DependencyHealth(
        boolean available,
        String detail
) {
}
