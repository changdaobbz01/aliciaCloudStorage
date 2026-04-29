package com.alicia.cloudstorage.api.config;

import com.alicia.cloudstorage.api.auth.AuthException;
import com.alicia.cloudstorage.api.dto.ApiErrorResponse;
import com.alicia.cloudstorage.api.service.CosStorageException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 统一处理登录状态和权限相关异常。
     */
    @ExceptionHandler(AuthException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ApiErrorResponse handleAuthException(AuthException ex) {
        return new ApiErrorResponse(401, ex.getMessage(), LocalDateTime.now());
    }

    /**
     * 统一处理业务参数校验失败异常。
     */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorResponse handleIllegalArgumentException(IllegalArgumentException ex) {
        return new ApiErrorResponse(400, ex.getMessage(), LocalDateTime.now());
    }

    /**
     * 统一处理基于注解的请求体验证异常。
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorResponse handleValidationException(MethodArgumentNotValidException ex) {
        FieldError fieldError = ex.getBindingResult().getFieldErrors().stream().findFirst().orElse(null);
        String message = fieldError == null ? "请求参数校验失败。" : fieldError.getDefaultMessage();
        return new ApiErrorResponse(400, message, LocalDateTime.now());
    }

    /**
     * 统一处理数据库唯一约束等冲突异常，并尽量返回更友好的冲突提示。
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorResponse handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        String detailMessage = ex.getMostSpecificCause() == null
                ? ex.getMessage()
                : ex.getMostSpecificCause().getMessage();
        String message = detailMessage != null && detailMessage.contains("uk_storage_node_owner_parent_active_name")
                ? "同级目录下已存在同名文件或文件夹。"
                : "提交的数据与现有记录冲突。";

        return new ApiErrorResponse(400, message, LocalDateTime.now());
    }

    /**
     * 统一处理腾讯 COS 访问异常，返回普通用户能理解的云存储错误。
     */
    @ExceptionHandler(CosStorageException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public ApiErrorResponse handleCosStorageException(CosStorageException ex) {
        return new ApiErrorResponse(502, ex.getMessage(), LocalDateTime.now());
    }

    /**
     * 兜底处理系统未预期异常，避免堆栈直接暴露给前端。
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiErrorResponse handleUnexpectedException(Exception ex) {
        log.error("Unhandled API exception", ex);
        return new ApiErrorResponse(500, "服务器发生未预期错误。", LocalDateTime.now());
    }
}
