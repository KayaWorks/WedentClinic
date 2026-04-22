package com.wedent.clinic.common.exception;

public class TenantScopeViolationException extends BusinessException {

    public TenantScopeViolationException(String message) {
        super(ErrorCode.TENANT_SCOPE_VIOLATION, message);
    }
}
