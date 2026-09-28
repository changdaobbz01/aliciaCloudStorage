package com.alicia.cloudstorage.ragexecution.api.internal;

import com.alicia.cloudstorage.ragexecution.application.PlanRegistrationException;
import com.alicia.cloudstorage.ragexecution.infrastructure.identity.IdentityAccessVerificationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice(assignableTypes = InternalExecutionRegistrationController.class)
public class InternalApiExceptionHandler {

    @ExceptionHandler(PlanRegistrationException.class)
    ResponseEntity<Map<String, String>> handlePlan(PlanRegistrationException exception) {
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
}
