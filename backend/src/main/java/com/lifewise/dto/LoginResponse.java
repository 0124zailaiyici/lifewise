package com.lifewise.dto;

import lombok.Data;

@Data
public class LoginResponse {
    private Long id;
    private String username;
    private String phone;
    private String avatar;
    private String token;
}
