package com.lifewise.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class AiServiceTest {

    @Autowired
    private AiService aiService;

    @Test
    void shouldReturnMockResponseWithoutApiKey() {
        String response = aiService.chat("西红柿炒鸡蛋怎么做", "cooking", 1L, null);
        assertNotNull(response);
        assertFalse(response.isEmpty());
    }

    @Test
    void shouldHandleDifferentScenes() {
        String response = aiService.chat("怎么挑西瓜", "shopping", 1L, null);
        assertNotNull(response);
        assertFalse(response.isEmpty());

        String response2 = aiService.chat("水龙头滴水怎么办", "repair", 1L, null);
        assertNotNull(response2);
        assertFalse(response2.isEmpty());

        String response3 = aiService.chat("衣服染色了怎么洗", "housework", 1L, null);
        assertNotNull(response3);
        assertFalse(response3.isEmpty());
    }

    @Test
    void shouldHandleUnknownScene() {
        String response = aiService.chat("你好", "unknown", 1L, null);
        assertNotNull(response);
        assertFalse(response.isEmpty());
    }

    @Test
    void shouldHandleEmptyMessage() {
        String response = aiService.chat("", "cooking", 1L, null);
        assertNotNull(response);
    }

    @Test
    void shouldReturnValidJsonForCookingQuery() {
        String response = aiService.chat("西红柿炒鸡蛋", "cooking", 1L, null);
        assertNotNull(response);
        assertFalse(response.isEmpty());
    }
}