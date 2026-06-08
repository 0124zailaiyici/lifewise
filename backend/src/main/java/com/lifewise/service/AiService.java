package com.lifewise.service;

import com.lifewise.dto.ChatRequest;
import com.lifewise.dto.AiReply;

public interface AiService {
    String chat(ChatRequest request, Long userId);
    AiReply chatWithMetadata(ChatRequest request, Long userId);
    String chat(String message, String scene, Long userId, Long conversationId, String imageUrl);
}
