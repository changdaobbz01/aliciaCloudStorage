package com.alicia.cloudstorage.api.ragexecution.application;

import com.alicia.cloudstorage.api.ragexecution.api.CloudActionRequest;
import com.alicia.cloudstorage.api.ragexecution.api.CloudActionResponse;
import com.alicia.cloudstorage.api.ragexecution.application.CloudActionRequestValidator.ValidatedCloudAction;
import com.alicia.cloudstorage.api.ragexecution.persistence.RagActionReceiptEntity;
import com.alicia.cloudstorage.api.ragexecution.persistence.RagActionReceiptRepository;
import com.alicia.cloudstorage.api.ragexecution.persistence.RagActionReceiptStatus;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.ZoneOffset;

@Service
public class CloudActionDispatchService {

    private final CloudActionRequestValidator validator;
    private final RagActionReceiptRepository receiptRepository;
    private final CloudActionTransactionalExecutor transactionalExecutor;
    private final JsonMapper jsonMapper;

    public CloudActionDispatchService(
            CloudActionRequestValidator validator,
            RagActionReceiptRepository receiptRepository,
            CloudActionTransactionalExecutor transactionalExecutor,
            JsonMapper jsonMapper
    ) {
        this.validator = validator;
        this.receiptRepository = receiptRepository;
        this.transactionalExecutor = transactionalExecutor;
        this.jsonMapper = jsonMapper;
    }

    public CloudActionResponse dispatch(CloudActionRequest request) {
        ValidatedCloudAction action = validator.validate(request);
        RagActionReceiptEntity existing = receiptRepository.findById(action.stepId()).orElse(null);
        if (existing != null) {
            return existingResponse(existing, action);
        }
        try {
            return transactionalExecutor.execute(action);
        } catch (DataIntegrityViolationException exception) {
            RagActionReceiptEntity concurrent = receiptRepository.findById(action.stepId()).orElse(null);
            if (concurrent != null) {
                return existingResponse(concurrent, action);
            }
            throw exception;
        }
    }

    private CloudActionResponse existingResponse(
            RagActionReceiptEntity receipt,
            ValidatedCloudAction action
    ) {
        if (!receipt.getExecutionId().equals(action.executionId())
                || !receipt.getActorUserId().equals(action.actorUserId())
                || receipt.getActionType() != action.actionType()
                || !receipt.getPayloadSchemaVersion().equals(action.payloadSchemaVersion())
                || !receipt.getRequestHash().equals(action.requestHash())) {
            throw new CloudActionException(409, "step_request_conflict");
        }
        if (receipt.getStatus() != RagActionReceiptStatus.SUCCEEDED
                || receipt.getCompletedAt() == null
                || receipt.getResultJson() == null) {
            throw new CloudActionException(409, "step_execution_in_progress");
        }
        return new CloudActionResponse(
                receipt.getExecutionId(),
                receipt.getStepId(),
                receipt.getStatus().name(),
                receipt.getResultCode(),
                parseResult(receipt.getResultJson()),
                receipt.getCompletedAt().toInstant(ZoneOffset.UTC)
        );
    }

    private JsonNode parseResult(String json) {
        try {
            return jsonMapper.readTree(json);
        } catch (Exception exception) {
            throw new CloudActionException(500, "stored_action_result_invalid", exception);
        }
    }
}
