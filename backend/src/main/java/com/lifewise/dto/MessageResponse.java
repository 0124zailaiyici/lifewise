package com.lifewise.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class MessageResponse {
    private Long id;
    private String role;
    private String content;
    private String imageUrl;
    private LocalDateTime createdAt;
}