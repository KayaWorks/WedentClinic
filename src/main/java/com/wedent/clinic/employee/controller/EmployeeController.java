package com.wedent.clinic.employee.controller;

import com.wedent.clinic.common.dto.ApiResponse;
import com.wedent.clinic.common.dto.PageResponse;
import com.wedent.clinic.employee.dto.EmployeeCreateRequest;
import com.wedent.clinic.employee.dto.EmployeeResponse;
import com.wedent.clinic.employee.dto.EmployeeUpdateRequest;
import com.wedent.clinic.employee.entity.EmployeeType;
import com.wedent.clinic.employee.service.EmployeeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Employees")
@RestController
@RequestMapping("/api/employees")
@RequiredArgsConstructor
public class EmployeeController {

    private final EmployeeService employeeService;

    @Operation(summary = "Create a new employee in the authenticated user's company scope")
    @PreAuthorize("hasAnyRole('CLINIC_OWNER','MANAGER')")
    @PostMapping
    public ResponseEntity<ApiResponse<EmployeeResponse>> create(@Valid @RequestBody EmployeeCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(employeeService.create(request)));
    }

    @Operation(summary = "List employees within company scope (paginated, filterable)")
    @PreAuthorize("hasAnyRole('CLINIC_OWNER','MANAGER','DOCTOR','STAFF')")
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<EmployeeResponse>>> list(
            @RequestParam(required = false) Long clinicId,
            @RequestParam(required = false) EmployeeType type,
            @ParameterObject Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(employeeService.search(clinicId, type, pageable)));
    }

    @Operation(summary = "Fetch employee by id")
    @PreAuthorize("hasAnyRole('CLINIC_OWNER','MANAGER','DOCTOR','STAFF')")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<EmployeeResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(employeeService.getById(id)));
    }

    @Operation(summary = "Update an existing employee")
    @PreAuthorize("hasAnyRole('CLINIC_OWNER','MANAGER')")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<EmployeeResponse>> update(
            @PathVariable Long id,
            @Valid @RequestBody EmployeeUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(employeeService.update(id, request)));
    }

    @Operation(summary = "Soft-delete an employee (active = false)")
    @PreAuthorize("hasAnyRole('CLINIC_OWNER','MANAGER')")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        employeeService.delete(id);
        return ResponseEntity.ok(ApiResponse.ok("Employee deleted"));
    }
}
