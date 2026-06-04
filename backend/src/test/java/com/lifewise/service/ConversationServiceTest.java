package com.lifewise.service;

import com.lifewise.dto.ConversationResponse;
import com.lifewise.entity.User;
import com.lifewise.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class ConversationServiceTest {

    @Autowired
    private ConversationService conversationService;

    @Autowired
    private UserRepository userRepository;

    private Long userId;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        User user = new User();
        user.setUsername("convuser");
        user.setEmail("conv@test.com");
        user.setPassword("pass");
        user = userRepository.save(user);
        userId = user.getId();
    }

    @Test
    void shouldCreateConversation() {
        ConversationResponse conv = conversationService.createConversation(userId, "怎么做红烧肉", "cooking");

        assertNotNull(conv);
        assertNotNull(conv.getId());
        assertEquals("怎么做红烧肉", conv.getTitle());
        assertEquals("cooking", conv.getScene());
        assertEquals("🍳", conv.getSceneIcon());
        assertEquals("做饭助手", conv.getSceneLabel());
    }

    @Test
    void shouldGetConversation() {
        ConversationResponse created = conversationService.createConversation(userId, "测试对话", "shopping");

        ConversationResponse found = conversationService.getConversation(created.getId());

        assertNotNull(found);
        assertEquals(created.getId(), found.getId());
        assertEquals("测试对话", found.getTitle());
    }

    @Test
    void shouldListUserConversations() {
        conversationService.createConversation(userId, "对话1", "cooking");
        conversationService.createConversation(userId, "对话2", "repair");
        conversationService.createConversation(userId, "对话3", "housework");

        List<ConversationResponse> list = conversationService.getUserConversations(userId);

        assertEquals(3, list.size());
    }

    @Test
    void shouldFilterByScene() {
        conversationService.createConversation(userId, "做菜", "cooking");
        conversationService.createConversation(userId, "修东西", "repair");
        conversationService.createConversation(userId, "再做一个菜", "cooking");

        List<ConversationResponse> cookingList = conversationService.getUserConversationsByScene(userId, "cooking");

        assertEquals(2, cookingList.size());

        List<ConversationResponse> repairList = conversationService.getUserConversationsByScene(userId, "repair");
        assertEquals(1, repairList.size());
    }

    @Test
    void shouldReturnEmptyListForNoConversations() {
        List<ConversationResponse> list = conversationService.getUserConversations(userId);
        assertTrue(list.isEmpty());
    }

    @Test
    void shouldThrowForNonExistentConversation() {
        assertThrows(RuntimeException.class, () -> conversationService.getConversation(99999L));
    }
}
