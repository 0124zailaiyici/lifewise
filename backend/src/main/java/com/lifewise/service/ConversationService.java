package com.lifewise.service;

import com.lifewise.dto.*;
import java.util.List;

public interface ConversationService {
    ConversationResponse createConversation(Long userId, String title, String scene);
    ConversationResponse getConversation(Long conversationId);
    List<ConversationResponse> getUserConversations(Long userId);
    List<ConversationResponse> getUserConversationsByScene(Long userId, String scene);
}
