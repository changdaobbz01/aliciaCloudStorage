package com.alicia.cloudstorage.ragexecution.infrastructure;

import com.alicia.cloudstorage.ragexecution.port.ExecutionClock;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

@Component
public class SystemExecutionClock implements ExecutionClock {

    private final Clock clock;

    public SystemExecutionClock(Clock clock) {
        this.clock = clock;
    }

    @Override
    public Instant now() {
        return clock.instant();
    }
}
