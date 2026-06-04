package com.lifewise.service;

public interface AiService {
    String chat(String message, String scene, Long userId, Long conversationId, String imageUrl);
}