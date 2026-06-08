package com.lifewise.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AiReply {
    private String content;
    private String source;
    private boolean externalCall;
    private String sourceLabel;
}
