package com.wedent.clinic.auth.service;

import com.wedent.clinic.auth.dto.LoginRequest;
import com.wedent.clinic.auth.dto.LoginResponse;

public interface AuthService {

    LoginResponse login(LoginRequest request);
}
