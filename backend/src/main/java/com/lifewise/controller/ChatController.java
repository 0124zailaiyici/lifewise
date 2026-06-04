package com.lifewise.controller;

import com.lifewise.common.ApiResponse;
import com.lifewise.dto.*;
import com.lifewise.entity.Message;
import com.lifewise.repository.MessageRepository;
import com.lifewise.service.AiService;
import com.lifewise.service.ConversationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final AiService aiService;
    private final ConversationService conversationService;
    private final MessageRepository messageRepository;

    @PostMapping("/send")
    public ApiResponse<ChatResponse> sendMessage(@RequestHeader Long userId,
                                                  @Valid @RequestBody ChatRequest request) {
        // 1. 创建或获取对话
        Long convId = request.getConversationId();
        if (convId == null) {
            String title = request.getMessage().length() > 50
                ? request.getMessage().substring(0, 50) + "..."
                : request.getMessage();
            var conv = conversationService.createConversation(userId, title,
                request.getScene() != null ? request.getScene() : "other");
            convId = conv.getId();
        }

        // 2. 保存用户消息
        Message userMsg = new Message();
        userMsg.setConversationId(convId);
        userMsg.setRole("user");
        userMsg.setContent(request.getMessage());
        messageRepository.save(userMsg);

        // 3. 调用 AI
        String aiResponse = aiService.chat(request.getMessage(), request.getScene(), userId);

        // 4. 保存 AI 回复
        Message aiMsg = new Message();
        aiMsg.setConversationId(convId);
        aiMsg.setRole("assistant");
        aiMsg.setContent(aiResponse);
        aiMsg = messageRepository.save(aiMsg);

        // 5. 返回
        ChatResponse resp = new ChatResponse();
        resp.setId(aiMsg.getId());
        resp.setConversationId(convId);
        resp.setRole(aiMsg.getRole());
        resp.setContent(aiMsg.getContent());
        resp.setCreatedAt(aiMsg.getCreatedAt());

        return ApiResponse.success(resp);
    }

    @GetMapping("/conversations")
    public ApiResponse<?> getConversations(@RequestHeader Long userId,
                                            @RequestParam(required = false) String scene) {
        if (scene != null && !scene.isEmpty()) {
            return ApiResponse.success(conversationService.getUserConversationsByScene(userId, scene));
        }
        return ApiResponse.success(conversationService.getUserConversations(userId));
    }

    @GetMapping("/conversations/{id}")
    public ApiResponse<?> getConversation(@PathVariable Long id) {
        return ApiResponse.success(conversationService.getConversation(id));
    }
}
