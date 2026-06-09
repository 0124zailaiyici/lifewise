package com.lifewise.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ChatRequest {
    @NotBlank
    private String message;
    private String scene;
    private Long conversationId;
    private String imageUrl;
    private String provider = "qwen";
    private String model;
    private Boolean followUp = false;
}
