package com.alicia.cloudstorage.identity.config;

import com.alicia.cloudstorage.identity.dto.IdentityErrorResponse;
import com.alicia.cloudstorage.identity.service.IdentityAuthException;
import org.springframework.http.HttpStatus;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@RestControllerAdvice
public class IdentityExceptionHandler {

    @ExceptionHandler(IdentityAuthException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public IdentityErrorResponse handleIdentityAuthException(IdentityAuthException ex) {
        return error(401, ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public IdentityErrorResponse handleIllegalArgumentException(IllegalArgumentException ex) {
        return error(400, ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public IdentityErrorResponse handleValidationException(MethodArgumentNotValidException ex) {
        FieldError fieldError = ex.getBindingResult().getFieldErrors().stream().findFirst().orElse(null);
        String message = fieldError == null ? "请求参数校验失败。" : fieldError.getDefaultMessage();
        return error(400, message);
    }

    private IdentityErrorResponse error(int status, String message) {
        return new IdentityErrorResponse(status, message, OffsetDateTime.now(ZoneOffset.UTC));
    }
}
