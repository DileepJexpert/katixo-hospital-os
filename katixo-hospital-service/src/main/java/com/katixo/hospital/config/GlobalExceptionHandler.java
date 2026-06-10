package com.katixo.hospital.config;

import com.katixo.hospital.common.dto.ApiResponse;
import com.katixo.hospital.common.exception.BusinessException;
import com.katixo.hospital.common.exception.TenantAccessDeniedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<?>> handleBusinessException(BusinessException ex, WebRequest request) {
        log.warn("Business exception: {} - {}", ex.getCode(), ex.getMessage());

        ApiResponse<?> response = ApiResponse.builder()
                .success(false)
                .status(HttpStatus.BAD_REQUEST.value())
                .error(ex.getCode())
                .message(ex.getMessage())
                .correlationId(UUID.randomUUID())
                .build();

        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(TenantAccessDeniedException.class)
    public ResponseEntity<ApiResponse<?>> handleTenantAccessDenied(TenantAccessDeniedException ex, WebRequest request) {
        log.warn("Tenant access denied: {}", ex.getMessage());

        ApiResponse<?> response = ApiResponse.builder()
                .success(false)
                .status(HttpStatus.FORBIDDEN.value())
                .error("ACCESS_DENIED")
                .message("Access denied")
                .correlationId(UUID.randomUUID())
                .build();

        return new ResponseEntity<>(response, HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<?>> handleValidationExceptions(MethodArgumentNotValidException ex, WebRequest request) {
        List<ApiResponse.ErrorDetail> errors = new ArrayList<>();

        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.add(ApiResponse.ErrorDetail.builder()
                    .field(fieldName)
                    .message(errorMessage)
                    .code("VALIDATION_ERROR")
                    .build());
        });

        ApiResponse<?> response = ApiResponse.builder()
                .success(false)
                .status(HttpStatus.UNPROCESSABLE_ENTITY.value())
                .error("VALIDATION_FAILED")
                .message("Validation failed")
                .correlationId(UUID.randomUUID())
                .errors(errors)
                .build();

        return new ResponseEntity<>(response, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<ApiResponse<?>> handleAccessDenied(org.springframework.security.access.AccessDeniedException ex,
                                                             WebRequest request) {
        ApiResponse<?> response = ApiResponse.builder()
                .success(false)
                .status(HttpStatus.FORBIDDEN.value())
                .error("ACCESS_DENIED")
                .message("Access denied")
                .correlationId(UUID.randomUUID())
                .build();
        return new ResponseEntity<>(response, HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<?>> handleGlobalException(Exception ex, WebRequest request) {
        log.error("Unexpected error", ex);

        ApiResponse<?> response = ApiResponse.builder()
                .success(false)
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .error("INTERNAL_ERROR")
                .message("Internal server error")
                .correlationId(UUID.randomUUID())
                .build();

        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
