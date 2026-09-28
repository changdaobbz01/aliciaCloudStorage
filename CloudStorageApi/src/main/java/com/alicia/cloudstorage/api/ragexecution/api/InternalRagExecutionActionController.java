package com.alicia.cloudstorage.api.ragexecution.api;

import com.alicia.cloudstorage.api.ragexecution.application.CloudActionDispatchService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnProperty(
        prefix = "alicia.rag-execution.internal-actions",
        name = "enabled",
        havingValue = "true"
)
public class InternalRagExecutionActionController {

    private final CloudActionDispatchService dispatchService;

    public InternalRagExecutionActionController(CloudActionDispatchService dispatchService) {
        this.dispatchService = dispatchService;
    }

    @PostMapping("/internal/rag-execution/actions")
    public CloudActionResponse dispatch(@RequestBody CloudActionRequest request) {
        return dispatchService.dispatch(request);
    }
}
