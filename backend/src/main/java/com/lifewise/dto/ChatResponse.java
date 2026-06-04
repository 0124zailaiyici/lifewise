package com.lifewise.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ChatResponse {
    private Long id;
    private Long conversationId;
    private String role;
    private String content;
    private String imageUrl;
    private LocalDateTime createdAt;
}