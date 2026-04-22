package com.wedent.clinic.common.exception;

import com.wedent.clinic.common.dto.ErrorResponse;
import com.wedent.clinic.common.dto.ErrorResponse.FieldErrorDetail;
import jakarta.persistence.OptimisticLockException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException ex, HttpServletRequest request) {
        log.warn("Business exception [{}]: {}", ex.getErrorCode().getCode(), ex.getMessage());
        ErrorResponse body = ErrorResponse.of(ex.getErrorCode().getCode(), ex.getMessage(), request.getRequestURI());
        return ResponseEntity.status(ex.getErrorCode().getHttpStatus()).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<FieldErrorDetail> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(this::toFieldErrorDetail)
                .toList();
        ErrorResponse body = ErrorResponse.of(
                ErrorCode.VALIDATION_ERROR.getCode(),
                "Validation failed",
                request.getRequestURI(),
                errors
        );
        return ResponseEntity.status(ErrorCode.VALIDATION_ERROR.getHttpStatus()).body(body);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ErrorResponse> handleBadRequest(Exception ex, HttpServletRequest request) {
        log.warn("Bad request: {}", ex.getMessage());
        ErrorResponse body = ErrorResponse.of(
                ErrorCode.INVALID_REQUEST.getCode(),
                "Malformed or invalid request",
                request.getRequestURI()
        );
        return ResponseEntity.status(ErrorCode.INVALID_REQUEST.getHttpStatus()).body(body);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrity(DataIntegrityViolationException ex, HttpServletRequest request) {
        log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());
        ErrorResponse body = ErrorResponse.of(
                ErrorCode.DUPLICATE_RESOURCE.getCode(),
                "Data integrity violation (duplicate or constraint failure)",
                request.getRequestURI()
        );
        return ResponseEntity.status(ErrorCode.DUPLICATE_RESOURCE.getHttpStatus()).body(body);
    }

    @ExceptionHandler({BadCredentialsException.class, AuthenticationException.class})
    public ResponseEntity<ErrorResponse> handleAuth(AuthenticationException ex, HttpServletRequest request) {
        log.warn("Authentication failed: {}", ex.getMessage());
        ErrorResponse body = ErrorResponse.of(
                ErrorCode.INVALID_CREDENTIALS.getCode(),
                "Invalid credentials",
                request.getRequestURI()
        );
        return ResponseEntity.status(ErrorCode.INVALID_CREDENTIALS.getHttpStatus()).body(body);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        ErrorResponse body = ErrorResponse.of(
                ErrorCode.ACCESS_DENIED.getCode(),
                "Access denied",
                request.getRequestURI()
        );
        return ResponseEntity.status(ErrorCode.ACCESS_DENIED.getHttpStatus()).body(body);
    }

    @ExceptionHandler(OptimisticLockException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLock(OptimisticLockException ex, HttpServletRequest request) {
        ErrorResponse body = ErrorResponse.of(
                ErrorCode.BUSINESS_RULE_VIOLATION.getCode(),
                "Resource modified by another user. Please retry.",
                request.getRequestURI()
        );
        return ResponseEntity.status(ErrorCode.BUSINESS_RULE_VIOLATION.getHttpStatus()).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unexpected error", ex);
        ErrorResponse body = ErrorResponse.of(
                ErrorCode.INTERNAL_ERROR.getCode(),
                "Unexpected error occurred",
                request.getRequestURI()
        );
        return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.getHttpStatus()).body(body);
    }

    private FieldErrorDetail toFieldErrorDetail(FieldError fe) {
        return new FieldErrorDetail(fe.getField(), fe.getDefaultMessage(), fe.getRejectedValue());
    }
}
