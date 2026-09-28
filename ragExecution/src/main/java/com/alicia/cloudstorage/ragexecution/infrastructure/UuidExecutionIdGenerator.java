package com.alicia.cloudstorage.ragexecution.infrastructure;

import com.alicia.cloudstorage.ragexecution.port.ExecutionIdGenerator;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class UuidExecutionIdGenerator implements ExecutionIdGenerator {

    @Override
    public UUID nextExecutionId() {
        return UUID.randomUUID();
    }

    @Override
    public UUID nextStepId() {
        return UUID.randomUUID();
    }
}
