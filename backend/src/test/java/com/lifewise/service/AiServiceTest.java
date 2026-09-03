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
        String response = aiService.chat("西红柿炒鸡蛋怎么做", "cooking", 1L, null, null);
        assertNotNull(response);
        assertFalse(response.isEmpty());
    }

    @Test
    void shouldHandleDifferentScenes() {
        String response = aiService.chat("怎么挑西瓜", "shopping", 1L, null, null);
        assertNotNull(response);
        assertFalse(response.isEmpty());

        String response2 = aiService.chat("水龙头滴水怎么办", "repair", 1L, null, null);
        assertNotNull(response2);
        assertFalse(response2.isEmpty());

        String response3 = aiService.chat("衣服染色了怎么洗", "housework", 1L, null, null);
        assertNotNull(response3);
        assertFalse(response3.isEmpty());
    }

    @Test
    void shouldHandleUnknownScene() {
        String response = aiService.chat("你好", "unknown", 1L, null, null);
        assertNotNull(response);
        assertFalse(response.isEmpty());
    }

    @Test
    void shouldHandleEmptyMessage() {
        String response = aiService.chat("", "cooking", 1L, null, null);
        assertNotNull(response);
    }

    @Test
    void shouldReturnValidJsonForCookingQuery() {
        String response = aiService.chat("西红柿炒鸡蛋", "cooking", 1L, null, null);
        assertNotNull(response);
        assertFalse(response.isEmpty());
    }

    @Test
    void shouldPrioritizeRepairSafetyOverAiOrCache() {
        String response = aiService.chat("插座冒烟还有焦味怎么办", "repair", 1L, null, null);

        assertTrue(response.contains("安全确认"));
        assertTrue(response.contains("建议专业人员"));
        assertTrue(response.contains("不要触碰设备"));
    }

    @Test
    void shouldPrioritizeHealthEmergencyOverAiOrCache() {
        String response = aiService.chat("突然胸痛而且喘不上气", "health", 1L, null, null);

        assertTrue(response.contains("紧急就医"));
        assertTrue(response.contains("立即联系急救服务"));
        assertTrue(response.contains("急救知识"));
    }

    @Test
    void shouldPrioritizePetEmergencyOverAiOrCache() {
        String response = aiService.chat("猫误食了百合怎么办", "pet", 1L, null, null);

        assertTrue(response.contains("紧急就诊"));
        assertTrue(response.contains("不要自行催吐"));
        assertTrue(response.contains("宠物医院"));
    }
}
