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
    public ApiResponse<ChatResponse> sendMessage(@RequestAttribute Long userId,
                                                  @Valid @RequestBody ChatRequest request) {
        Long convId = request.getConversationId();
        if (convId == null) {
            String title = request.getMessage().length() > 50
                ? request.getMessage().substring(0, 50) + "..."
                : request.getMessage();
            var conv = conversationService.createConversation(userId, title,
                request.getScene() != null ? request.getScene() : "other");
            convId = conv.getId();
        }

        Message userMsg = new Message();
        userMsg.setConversationId(convId);
        userMsg.setRole("user");
        userMsg.setContent(request.getMessage());
        messageRepository.save(userMsg);

        String aiResponse = aiService.chat(request.getMessage(), request.getScene(), userId);

        Message aiMsg = new Message();
        aiMsg.setConversationId(convId);
        aiMsg.setRole("assistant");
        aiMsg.setContent(aiResponse);
        aiMsg = messageRepository.save(aiMsg);

        ChatResponse resp = new ChatResponse();
        resp.setId(aiMsg.getId());
        resp.setConversationId(convId);
        resp.setRole(aiMsg.getRole());
        resp.setContent(aiMsg.getContent());
        resp.setCreatedAt(aiMsg.getCreatedAt());

        return ApiResponse.success(resp);
    }

    @GetMapping("/conversations")
    public ApiResponse<?> getConversations(@RequestAttribute Long userId,
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
