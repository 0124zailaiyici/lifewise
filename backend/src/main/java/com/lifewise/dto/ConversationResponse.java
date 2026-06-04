package com.lifewise.dto;

import com.lifewise.common.Scene;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class ConversationResponse {
    private Long id;
    private String title;
    private String scene;
    private String sceneIcon;
    private String sceneLabel;
    private List<MessageResponse> messages;
    private LocalDateTime createdAt;
}
