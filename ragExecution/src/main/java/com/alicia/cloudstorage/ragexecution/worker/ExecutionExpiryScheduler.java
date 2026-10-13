package com.alicia.cloudstorage.ragexecution.worker;

import com.alicia.cloudstorage.ragexecution.config.RagExecutionFeatureProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ExecutionExpiryScheduler {

    static final int MAX_EXPIRATIONS_PER_POLL = 100;

    private static final Logger log = LoggerFactory.getLogger(ExecutionExpiryScheduler.class);

    private final RagExecutionFeatureProperties features;
    private final ExecutionWorkStateService stateService;

    public ExecutionExpiryScheduler(
            RagExecutionFeatureProperties features,
            ExecutionWorkStateService stateService
    ) {
        this.features = features;
        this.stateService = stateService;
    }

    @Scheduled(fixedDelayString = "${alicia.rag-execution.maintenance.poll-delay:1s}")
    public void poll() {
        if (!features.enabled()) {
            return;
        }
        try {
            for (int processed = 0; processed < MAX_EXPIRATIONS_PER_POLL; processed++) {
                if (!stateService.expireOne()) {
                    return;
                }
            }
        } catch (RuntimeException exception) {
            log.error(
                    "RAG execution expiry maintenance failed: category={}",
                    exception.getClass().getSimpleName()
            );
        }
    }
}
