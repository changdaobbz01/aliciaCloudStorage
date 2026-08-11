package com.alicia.cloudstorage.rag.controller;

import com.alicia.cloudstorage.rag.dto.ApiMessageResponse;
import com.alicia.cloudstorage.rag.dto.StorageNodeChangeRequest;
import com.alicia.cloudstorage.rag.security.WebhookSignatureVerifier;
import com.alicia.cloudstorage.rag.service.StorageEventIngestionService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

import java.util.Set;

@RestController
@RequestMapping("/internal/storage-events")
public class StorageEventController {

    private static final String SIGNATURE_HEADER = "X-Alicia-Event-Signature";

    private final WebhookSignatureVerifier signatureVerifier;
    private final ObjectMapper objectMapper;
    private final Validator validator;
    private final StorageEventIngestionService ingestionService;

    public StorageEventController(
            WebhookSignatureVerifier signatureVerifier,
            ObjectMapper objectMapper,
            Validator validator,
            StorageEventIngestionService ingestionService
    ) {
        this.signatureVerifier = signatureVerifier;
        this.objectMapper = objectMapper;
        this.validator = validator;
        this.ingestionService = ingestionService;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiMessageResponse ingestStorageEvent(
            @RequestHeader(value = SIGNATURE_HEADER, required = false) String signature,
            @RequestBody String body
    ) {
        signatureVerifier.verify(body, signature);
        StorageNodeChangeRequest request = parseRequest(body);
        ingestionService.ingest(validateRequest(request));
        return new ApiMessageResponse("accepted");
    }

    private StorageNodeChangeRequest parseRequest(String body) {
        try {
            return objectMapper.readValue(body, StorageNodeChangeRequest.class);
        } catch (JsonProcessingException exception) {
            throw new ResponseStatusException(BAD_REQUEST, "Invalid storage event payload.", exception);
        }
    }

    private StorageNodeChangeRequest validateRequest(StorageNodeChangeRequest request) {
        Set<ConstraintViolation<StorageNodeChangeRequest>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "Invalid storage event payload.");
        }

        return request;
    }
}
