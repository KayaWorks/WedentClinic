package com.wedent.clinic.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        boolean success,
        String code,
        String message,
        String path,
        List<FieldErrorDetail> errors,
        Map<String, Object> meta,
        Instant timestamp
) {

    public record FieldErrorDetail(String field, String message, Object rejectedValue) {}

    public static ErrorResponse of(String code, String message, String path) {
        return new ErrorResponse(false, code, message, path, null, null, Instant.now());
    }

    public static ErrorResponse of(String code, String message, String path, List<FieldErrorDetail> errors) {
        return new ErrorResponse(false, code, message, path, errors, null, Instant.now());
    }
}
