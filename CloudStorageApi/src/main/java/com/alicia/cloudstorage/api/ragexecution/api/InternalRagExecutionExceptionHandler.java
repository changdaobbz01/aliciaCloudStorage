package com.alicia.cloudstorage.api.ragexecution.api;

import com.alicia.cloudstorage.api.ragexecution.application.CloudActionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice(assignableTypes = InternalRagExecutionActionController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class InternalRagExecutionExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(InternalRagExecutionExceptionHandler.class);

    @ExceptionHandler(CloudActionException.class)
    ResponseEntity<Map<String, String>> handleCloudAction(CloudActionException exception) {
        return ResponseEntity.status(exception.statusCode()).body(Map.of("error", exception.errorCode()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<Map<String, String>> handleUnreadable(HttpMessageNotReadableException exception) {
        return ResponseEntity.badRequest().body(Map.of("error", "invalid_json"));
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ResponseEntity<Map<String, String>> handleOptimisticConflict(ObjectOptimisticLockingFailureException exception) {
        return ResponseEntity.status(409).body(Map.of("error", "resource_version_conflict"));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<Map<String, String>> handleDataConflict(DataIntegrityViolationException exception) {
        return ResponseEntity.status(409).body(Map.of("error", "business_rule_conflict"));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Map<String, String>> handleUnexpected(Exception exception) {
        log.error("Internal RAG action failed: category={}", exception.getClass().getSimpleName());
        return ResponseEntity.internalServerError().body(Map.of("error", "internal_action_failed"));
    }
}
