package com.lifewise.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class FavoriteResponse {
    private Long id;
    private Long messageId;
    private Long conversationId;
    private String summary;
    private String scene;
    private String note;
    private String category;
    private LocalDateTime createdAt;
}
