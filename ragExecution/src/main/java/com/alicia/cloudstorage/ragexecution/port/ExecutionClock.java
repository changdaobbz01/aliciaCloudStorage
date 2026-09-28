package com.alicia.cloudstorage.ragexecution.port;

import java.time.Instant;

@FunctionalInterface
public interface ExecutionClock {

    Instant now();
}
