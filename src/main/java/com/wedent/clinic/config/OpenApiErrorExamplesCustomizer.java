package com.wedent.clinic.config;

import com.wedent.clinic.common.dto.ErrorResponse;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.core.converter.ResolvedSchema;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.springdoc.core.customizers.GlobalOpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Attaches a consistent error-response envelope to every generated OpenAPI
 * operation so Swagger UI mirrors the actual contract emitted by
 * {@link com.wedent.clinic.common.exception.GlobalExceptionHandler}.
 *
 * <p>Rationale: without this customizer, Swagger UI only shows the happy-path
 * response for each endpoint. Consumers of the API (frontend, mobile, partner
 * integrations) then have to reverse-engineer the error shape by experimentation.
 * Surfacing the canonical {@code ErrorResponse} schema plus a worked example
 * per HTTP status makes the contract self-documenting and reduces integration
 * churn.</p>
 *
 * <p>Strategy:
 * <ul>
 *   <li>Register the {@code ErrorResponse} schema once under {@code components.schemas}.</li>
 *   <li>Register one reusable {@code components.responses} entry per status code,
 *       each carrying a concrete JSON example that matches our real payload.</li>
 *   <li>Walk every operation and attach the relevant subset of error responses
 *       via {@code $ref}, skipping any status the developer has already
 *       annotated explicitly (so custom examples win).</li>
 * </ul>
 * </p>
 */
@Configuration
public class OpenApiErrorExamplesCustomizer {

    private static final String ERROR_SCHEMA_NAME = "ErrorResponse";
    private static final String ERROR_SCHEMA_REF = "#/components/schemas/" + ERROR_SCHEMA_NAME;

    // Reusable response keys under components.responses — referenced from every op.
    private static final String RESP_BAD_REQUEST         = "BadRequest";
    private static final String RESP_UNAUTHORIZED        = "Unauthorized";
    private static final String RESP_FORBIDDEN           = "Forbidden";
    private static final String RESP_NOT_FOUND           = "NotFound";
    private static final String RESP_CONFLICT            = "Conflict";
    private static final String RESP_UNPROCESSABLE       = "UnprocessableEntity";
    private static final String RESP_TOO_MANY_REQUESTS   = "TooManyRequests";
    private static final String RESP_INTERNAL_ERROR      = "InternalServerError";

    @Bean
    public GlobalOpenApiCustomizer errorResponsesCustomizer() {
        return openApi -> {
            Components components = ensureComponents(openApi);
            registerErrorSchema(components);
            registerReusableErrorResponses(components);

            if (openApi.getPaths() == null) {
                return;
            }
            openApi.getPaths().forEach((path, pathItem) ->
                    pathItem.readOperationsMap().forEach((httpMethod, operation) -> {
                        ApiResponses responses = operation.getResponses() != null
                                ? operation.getResponses()
                                : new ApiResponses();

                        // Error status matrix applied per HTTP verb. We skip any status
                        // the author already defined so hand-authored examples win.
                        for (String status : applicableStatuses(path, httpMethod)) {
                            if (responses.containsKey(status)) {
                                continue;
                            }
                            responses.addApiResponse(status, refResponse(statusToResponseKey(status)));
                        }
                        operation.setResponses(responses);
                    })
            );
        };
    }

    // ---------------------------------------------------------------------
    // Components wiring
    // ---------------------------------------------------------------------

    private static Components ensureComponents(OpenAPI openApi) {
        if (openApi.getComponents() == null) {
            openApi.setComponents(new Components());
        }
        return openApi.getComponents();
    }

    /**
     * Resolves the {@code ErrorResponse} record into a fully inlined OpenAPI
     * schema (including nested {@code FieldErrorDetail}) exactly once so that
     * individual operations can {@code $ref} it instead of re-emitting it.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void registerErrorSchema(Components components) {
        if (components.getSchemas() != null && components.getSchemas().containsKey(ERROR_SCHEMA_NAME)) {
            return;
        }
        ResolvedSchema resolved = ModelConverters.getInstance()
                .resolveAsResolvedSchema(new io.swagger.v3.core.converter.AnnotatedType(ErrorResponse.class));
        if (resolved != null && resolved.schema != null) {
            components.addSchemas(ERROR_SCHEMA_NAME, resolved.schema);
            if (resolved.referencedSchemas != null) {
                resolved.referencedSchemas.forEach((name, schema) -> {
                    if (components.getSchemas() == null || !components.getSchemas().containsKey(name)) {
                        components.addSchemas(name, schema);
                    }
                });
            }
        } else {
            // Fallback: minimal hand-built schema if introspection fails for any reason.
            components.addSchemas(ERROR_SCHEMA_NAME, new Schema<>().type("object"));
        }
    }

    private static void registerReusableErrorResponses(Components components) {
        putResponseIfAbsent(components, RESP_BAD_REQUEST,
                buildResponse("Request could not be processed due to validation or malformed payload.",
                        Map.of(
                                "VALIDATION_ERROR", exampleValidation(),
                                "INVALID_REQUEST",  exampleInvalidRequest()
                        )));

        putResponseIfAbsent(components, RESP_UNAUTHORIZED,
                buildResponse("Authentication is required or the provided credentials are invalid.",
                        Map.of(
                                "UNAUTHORIZED",         exampleUnauthorized(),
                                "INVALID_CREDENTIALS",  exampleInvalidCredentials()
                        )));

        putResponseIfAbsent(components, RESP_FORBIDDEN,
                buildResponse("Authenticated caller lacks the required privileges or is reaching outside their tenant scope.",
                        Map.of(
                                "ACCESS_DENIED",            exampleAccessDenied(),
                                "TENANT_SCOPE_VIOLATION",   exampleTenantScopeViolation()
                        )));

        putResponseIfAbsent(components, RESP_NOT_FOUND,
                buildResponse("Target resource does not exist (or is outside the caller's tenant).",
                        Map.of("RESOURCE_NOT_FOUND", exampleResourceNotFound())));

        putResponseIfAbsent(components, RESP_CONFLICT,
                buildResponse("Request conflicts with existing state (duplicate unique key or overlapping appointment).",
                        Map.of(
                                "DUPLICATE_RESOURCE",     exampleDuplicateResource(),
                                "APPOINTMENT_CONFLICT",   exampleAppointmentConflict()
                        )));

        putResponseIfAbsent(components, RESP_UNPROCESSABLE,
                buildResponse("Request is syntactically valid but violates a domain business rule.",
                        Map.of("BUSINESS_RULE_VIOLATION", exampleBusinessRuleViolation())));

        putResponseIfAbsent(components, RESP_TOO_MANY_REQUESTS,
                buildResponse("Rate limit exceeded. Retry after the cool-down window.",
                        Map.of("TOO_MANY_REQUESTS", exampleTooManyRequests())));

        putResponseIfAbsent(components, RESP_INTERNAL_ERROR,
                buildResponse("Unexpected server error. Correlate by `traceId` in the server logs.",
                        Map.of("INTERNAL_ERROR", exampleInternalError())));
    }

    private static void putResponseIfAbsent(Components components, String key, ApiResponse response) {
        if (components.getResponses() != null && components.getResponses().containsKey(key)) {
            return;
        }
        components.addResponses(key, response);
    }

    private static ApiResponse buildResponse(String description, Map<String, Example> examples) {
        MediaType mediaType = new MediaType()
                .schema(new Schema<>().$ref(ERROR_SCHEMA_REF));
        // Preserve declaration order — Swagger UI renders examples in iteration order.
        LinkedHashMap<String, Example> ordered = new LinkedHashMap<>(examples);
        mediaType.setExamples(ordered);

        Content content = new Content().addMediaType("application/json", mediaType);
        return new ApiResponse()
                .description(description)
                .content(content);
    }

    private static ApiResponse refResponse(String componentsResponseKey) {
        return new ApiResponse().$ref("#/components/responses/" + componentsResponseKey);
    }

    // ---------------------------------------------------------------------
    // Per-operation status selection
    // ---------------------------------------------------------------------

    /**
     * Conservative mapping of HTTP verb → likely error statuses, mirroring the
     * real {@link com.wedent.clinic.common.exception.GlobalExceptionHandler}.
     * We over-document slightly (e.g. attaching 403 to every authenticated
     * endpoint) because missing an error surface is a worse developer
     * experience than a redundant one.
     */
    private static List<String> applicableStatuses(String path, PathItem.HttpMethod method) {
        boolean isAuthPath = path != null && path.startsWith("/api/auth");
        boolean writes = switch (method) {
            case POST, PUT, PATCH, DELETE -> true;
            default -> false;
        };
        boolean hasBody = method == PathItem.HttpMethod.POST
                || method == PathItem.HttpMethod.PUT
                || method == PathItem.HttpMethod.PATCH;

        var statuses = new java.util.ArrayList<String>(8);
        if (hasBody) {
            statuses.add("400");
        }
        statuses.add("401");
        if (!isAuthPath) {
            statuses.add("403");
        }
        if (path != null && path.contains("{")) {
            statuses.add("404");
        }
        if (writes) {
            statuses.add("409");
            statuses.add("422");
        }
        if (isAuthPath) {
            statuses.add("429");
        }
        statuses.add("500");
        return statuses;
    }

    private static String statusToResponseKey(String status) {
        return switch (status) {
            case "400" -> RESP_BAD_REQUEST;
            case "401" -> RESP_UNAUTHORIZED;
            case "403" -> RESP_FORBIDDEN;
            case "404" -> RESP_NOT_FOUND;
            case "409" -> RESP_CONFLICT;
            case "422" -> RESP_UNPROCESSABLE;
            case "429" -> RESP_TOO_MANY_REQUESTS;
            case "500" -> RESP_INTERNAL_ERROR;
            default    -> RESP_INTERNAL_ERROR;
        };
    }

    // ---------------------------------------------------------------------
    // Example payloads — kept in sync with ErrorResponse.of(...)
    // ---------------------------------------------------------------------

    private static Example exampleValidation() {
        return new Example().summary("Bean Validation failure").value("""
                {
                  "success": false,
                  "code": "VALIDATION_ERROR",
                  "message": "Validation failed",
                  "path": "/api/appointments",
                  "errors": [
                    { "field": "startTime", "message": "must not be null", "rejectedValue": null },
                    { "field": "endTime",   "message": "must not be null", "rejectedValue": null }
                  ],
                  "timestamp": "2026-04-22T10:15:30Z"
                }
                """);
    }

    private static Example exampleInvalidRequest() {
        return new Example().summary("Malformed JSON or unparsable parameter").value("""
                {
                  "success": false,
                  "code": "INVALID_REQUEST",
                  "message": "Malformed or invalid request",
                  "path": "/api/appointments",
                  "timestamp": "2026-04-22T10:15:30Z"
                }
                """);
    }

    private static Example exampleUnauthorized() {
        return new Example().summary("Missing or expired JWT").value("""
                {
                  "success": false,
                  "code": "UNAUTHORIZED",
                  "message": "Authentication required",
                  "path": "/api/appointments",
                  "timestamp": "2026-04-22T10:15:30Z"
                }
                """);
    }

    private static Example exampleInvalidCredentials() {
        return new Example().summary("Bad username/password at login").value("""
                {
                  "success": false,
                  "code": "INVALID_CREDENTIALS",
                  "message": "Invalid credentials",
                  "path": "/api/auth/login",
                  "timestamp": "2026-04-22T10:15:30Z"
                }
                """);
    }

    private static Example exampleAccessDenied() {
        return new Example().summary("Role lacks permission for this endpoint").value("""
                {
                  "success": false,
                  "code": "ACCESS_DENIED",
                  "message": "Access denied",
                  "path": "/api/appointments/42",
                  "timestamp": "2026-04-22T10:15:30Z"
                }
                """);
    }

    private static Example exampleTenantScopeViolation() {
        return new Example().summary("Caller reached outside their company/clinic scope").value("""
                {
                  "success": false,
                  "code": "TENANT_SCOPE_VIOLATION",
                  "message": "Resource does not belong to the caller's tenant",
                  "path": "/api/patients/42",
                  "timestamp": "2026-04-22T10:15:30Z"
                }
                """);
    }

    private static Example exampleResourceNotFound() {
        return new Example().summary("Entity not found by id").value("""
                {
                  "success": false,
                  "code": "RESOURCE_NOT_FOUND",
                  "message": "Appointment with id 42 not found",
                  "path": "/api/appointments/42",
                  "timestamp": "2026-04-22T10:15:30Z"
                }
                """);
    }

    private static Example exampleDuplicateResource() {
        return new Example().summary("Unique-constraint violation (e.g. email already exists)").value("""
                {
                  "success": false,
                  "code": "DUPLICATE_RESOURCE",
                  "message": "Data integrity violation (duplicate or constraint failure)",
                  "path": "/api/patients",
                  "timestamp": "2026-04-22T10:15:30Z"
                }
                """);
    }

    private static Example exampleAppointmentConflict() {
        return new Example().summary("Doctor already booked in the requested time slot").value("""
                {
                  "success": false,
                  "code": "APPOINTMENT_CONFLICT",
                  "message": "Doctor already has an appointment in the requested time slot",
                  "path": "/api/appointments",
                  "timestamp": "2026-04-22T10:15:30Z"
                }
                """);
    }

    private static Example exampleBusinessRuleViolation() {
        return new Example().summary("Domain rule rejected the request (e.g. illegal status transition)").value("""
                {
                  "success": false,
                  "code": "BUSINESS_RULE_VIOLATION",
                  "message": "Illegal status transition: COMPLETED -> CREATED",
                  "path": "/api/appointments/42/status",
                  "timestamp": "2026-04-22T10:15:30Z"
                }
                """);
    }

    private static Example exampleTooManyRequests() {
        return new Example().summary("Login rate limit tripped").value("""
                {
                  "success": false,
                  "code": "TOO_MANY_REQUESTS",
                  "message": "Too many login attempts. Try again later.",
                  "path": "/api/auth/login",
                  "timestamp": "2026-04-22T10:15:30Z"
                }
                """);
    }

    private static Example exampleInternalError() {
        return new Example().summary("Unhandled server error").value("""
                {
                  "success": false,
                  "code": "INTERNAL_ERROR",
                  "message": "Unexpected error occurred",
                  "path": "/api/appointments",
                  "timestamp": "2026-04-22T10:15:30Z"
                }
                """);
    }
}
