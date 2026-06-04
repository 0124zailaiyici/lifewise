package com.lifewise.controller;

import com.lifewise.common.ApiResponse;
import com.lifewise.dto.LoginRequest;
import com.lifewise.dto.RegisterRequest;
import com.lifewise.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class SearchControllerTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserService userService;

    private String token;

    @BeforeEach
    void setUp() {
        RegisterRequest reg = new RegisterRequest();
        reg.setUsername("searchtest");
        reg.setPhone("13800138008");
        reg.setPassword("pass123");
        try {
            userService.register(reg);
        } catch (Exception e) {
            // 可能已存在
        }

        LoginRequest login = new LoginRequest();
        login.setPhone("13800138008");
        login.setPassword("pass123");
        var loginResp = userService.login(login);
        token = loginResp.getToken();
    }

    @Test
    void shouldSearchWithKeyword() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + token);

        ResponseEntity<ApiResponse> response = restTemplate.exchange(
            "/api/search?q=test",
            HttpMethod.GET,
            new HttpEntity<>(headers),
            ApiResponse.class
        );

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(200, response.getBody().getCode());
    }

    @Test
    void shouldReturn401WithoutToken() {
        ResponseEntity<ApiResponse> response = restTemplate.exchange(
            "/api/search?q=test",
            HttpMethod.GET,
            null,
            ApiResponse.class
        );

        assertEquals(401, response.getStatusCode().value());
    }

    @Test
    void shouldReturnEmptyResultsForRandomKeyword() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + token);

        ResponseEntity<ApiResponse> response = restTemplate.exchange(
            "/api/search?q=xyznonexistent12345",
            HttpMethod.GET,
            new HttpEntity<>(headers),
            ApiResponse.class
        );

        assertEquals(200, response.getStatusCode().value());
        Map<String, Object> data = (Map<String, Object>) response.getBody().getData();
        assertNotNull(data);
        assertEquals(0, data.get("total"));
    }
}
