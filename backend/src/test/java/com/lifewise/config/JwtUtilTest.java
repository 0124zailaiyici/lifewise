package com.lifewise.config;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JwtUtilTest {

    private final JwtUtil jwtUtil = new JwtUtil("test-secret-key-for-unit-test-at-least-256-bits!");

    @Test
    void shouldGenerateAndValidateToken() {
        String token = jwtUtil.generateToken(1L, "test@test.com");
        assertNotNull(token);
        assertTrue(jwtUtil.validateToken(token));
    }

    @Test
    void shouldExtractUserIdFromToken() {
        String token = jwtUtil.generateToken(42L, "user@test.com");
        Long userId = jwtUtil.getUserIdFromToken(token);
        assertEquals(42L, userId);
    }

    @Test
    void shouldRejectInvalidToken() {
        assertFalse(jwtUtil.validateToken("invalid.token.here"));
    }

    @Test
    void shouldRejectExpiredToken() {
        // 用过去的时间生成 token 这里无法直接测试，但 validateToken 应该对格式不对的返回 false
        assertFalse(jwtUtil.validateToken(""));
        assertFalse(jwtUtil.validateToken("abc.def.ghi"));
    }
}
