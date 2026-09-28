package com.alicia.cloudstorage.ragexecution.api.publicapi;

import com.alicia.cloudstorage.ragexecution.application.ExecutionAccessException;
import com.alicia.cloudstorage.ragexecution.infrastructure.identity.IdentityAccessVerificationException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice(assignableTypes = PublicExecutionController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class PublicExecutionExceptionHandler {

    @ExceptionHandler(ExecutionAccessException.class)
    ResponseEntity<Map<String, String>> handleAccess(ExecutionAccessException exception) {
        return ResponseEntity.status(exception.statusCode()).body(Map.of("error", exception.errorCode()));
    }

    @ExceptionHandler(IdentityAccessVerificationException.class)
    ResponseEntity<Map<String, String>> handleIdentity(IdentityAccessVerificationException exception) {
        return ResponseEntity.status(exception.statusCode()).body(Map.of("error", exception.errorCode()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<Map<String, String>> handleUnreadable(HttpMessageNotReadableException exception) {
        return ResponseEntity.badRequest().body(Map.of("error", "invalid_json"));
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ResponseEntity<Map<String, String>> handleOptimisticConflict(ObjectOptimisticLockingFailureException exception) {
        return ResponseEntity.status(409).body(Map.of("error", "execution_version_conflict"));
    }
}
