package com.lifewise.service;

import com.lifewise.dto.*;

public interface UserService {
    UserResponse register(RegisterRequest request);
    LoginResponse login(LoginRequest request);
    UserResponse getUserById(Long id);
}
