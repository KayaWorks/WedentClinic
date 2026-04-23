package com.wedent.clinic.auth.service;

import com.wedent.clinic.auth.dto.LoginRequest;
import com.wedent.clinic.auth.dto.LoginResponse;
import com.wedent.clinic.auth.dto.RefreshResponse;

public interface AuthService {

    LoginResponse login(LoginRequest request, String ipAddress, String userAgent);

    RefreshResponse refresh(String rawRefreshToken, String ipAddress, String userAgent);

    void logout(String rawRefreshToken);
}
