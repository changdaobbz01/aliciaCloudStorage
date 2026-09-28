package com.alicia.cloudstorage.ragexecution.port;

import java.util.UUID;

public interface ExecutionIdGenerator {

    UUID nextExecutionId();

    UUID nextStepId();
}
