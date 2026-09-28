package com.alicia.cloudstorage.ragexecution.worker;

import com.alicia.cloudstorage.ragexecution.config.RagExecutionFeatureProperties;
import com.alicia.cloudstorage.ragexecution.domain.ExecutionLease;
import com.alicia.cloudstorage.ragexecution.infrastructure.cloud.CloudActionDispatchException;
import com.alicia.cloudstorage.ragexecution.port.CloudActionGateway;
import com.alicia.cloudstorage.ragexecution.port.CloudActionGateway.CloudActionCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class ExecutionWorker {

    private static final Logger log = LoggerFactory.getLogger(ExecutionWorker.class);

    private final RagExecutionFeatureProperties features;
    private final ExecutionLeaseCoordinator leaseCoordinator;
    private final ExecutionWorkStateService stateService;
    private final CloudActionGateway cloudActionGateway;
    private final String workerId = "worker-" + UUID.randomUUID();

    public ExecutionWorker(
            RagExecutionFeatureProperties features,
            ExecutionLeaseCoordinator leaseCoordinator,
            ExecutionWorkStateService stateService,
            CloudActionGateway cloudActionGateway
    ) {
        this.features = features;
        this.leaseCoordinator = leaseCoordinator;
        this.stateService = stateService;
        this.cloudActionGateway = cloudActionGateway;
    }

    @Scheduled(fixedDelayString = "${alicia.rag-execution.worker.poll-delay:1s}")
    public void poll() {
        if (!features.enabled() || !features.workerEnabled()) {
            return;
        }
        try {
            stateService.expireOne();
            stateService.requeueOneReadyRetry();
            if (!features.cloudDispatchEnabled()) {
                return;
            }
            Optional<ExecutionLease> lease = leaseCoordinator.claimNext(workerId);
            lease.ifPresent(this::execute);
        } catch (RuntimeException exception) {
            log.error("RAG execution worker poll failed: category={}", exception.getClass().getSimpleName());
        }
    }

    private void execute(ExecutionLease lease) {
        Optional<CloudActionCommand> prepared = stateService.prepare(lease.executionId(), workerId);
        if (prepared.isEmpty()) {
            return;
        }
        CloudActionCommand command = prepared.get();
        try {
            stateService.succeed(
                    command.executionId(),
                    command.stepId(),
                    workerId,
                    cloudActionGateway.dispatch(command)
            );
        } catch (CloudActionDispatchException exception) {
            stateService.fail(
                    command.executionId(),
                    command.stepId(),
                    workerId,
                    exception.errorCode(),
                    exception.retryable()
            );
        } catch (RuntimeException exception) {
            log.error(
                    "RAG execution dispatch failed: executionId={} stepId={} actionType={} category={}",
                    command.executionId(),
                    command.stepId(),
                    command.actionType(),
                    exception.getClass().getSimpleName()
            );
            stateService.fail(
                    command.executionId(),
                    command.stepId(),
                    workerId,
                    "worker_internal_error",
                    true
            );
        }
    }
}
