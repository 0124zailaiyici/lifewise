package com.lifewise.controller;

import com.lifewise.common.ApiResponse;
import com.lifewise.dto.*;
import com.lifewise.entity.Message;
import com.lifewise.repository.MessageRepository;
import com.lifewise.service.AiService;
import com.lifewise.service.ConversationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import java.util.Map;
import org.springframework.web.bind.annotation.*;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

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

        // Call AI first with the full ChatRequest (includes provider/model)
        String aiResponse = aiService.chat(request, userId);

        // Create conversation if new (after AI call, so AiServiceImpl sees null convId for cache)
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
        if (request.getImageUrl() != null && !request.getImageUrl().isEmpty()) {
            userMsg.setImageUrl(request.getImageUrl());
        }
        messageRepository.save(userMsg);

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

    @PutMapping("/conversations/{id}/rename")
    public ApiResponse<?> renameConversation(@RequestAttribute Long userId,
                                                  @PathVariable Long id,
                                                  @RequestBody Map<String, String> body) {
        String newTitle = body.get("title");
        if (newTitle == null || newTitle.trim().isEmpty()) {
            return ApiResponse.error("标题不能为空");
        }
        return ApiResponse.success(conversationService.renameConversation(id, userId, newTitle.trim()));
    }
    @DeleteMapping("/conversations/{id}")
    public ApiResponse<?> deleteConversation(@RequestAttribute Long userId,
                                                  @PathVariable Long id) {
        conversationService.deleteConversation(id, userId);
        return ApiResponse.success(null);
    }

    @GetMapping("/conversations")
    public ApiResponse<?> getConversations(@RequestAttribute Long userId,
                                            @RequestParam(required = false) String scene) {
        if (scene != null && !scene.isEmpty()) {
            return ApiResponse.success(conversationService.getUserConversationsByScene(userId, scene));
        }
        return ApiResponse.success(conversationService.getUserConversations(userId));
    }

    @PostMapping("/export")
    public ApiResponse<?> exportConversation(@RequestBody Map<String, String> body) {
        String text = body.get("text");
        if (text == null || text.trim().isEmpty()) {
            return ApiResponse.error("导出内容不能为空");
        }
        try {
            String exportDir = System.getProperty("user.dir") + File.separator + "exports";
            Files.createDirectories(Paths.get(exportDir));
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String fileName = "对话记录_" + timestamp + ".txt";
            Path filePath = Paths.get(exportDir, fileName);
            Files.writeString(filePath, text, java.nio.charset.StandardCharsets.UTF_8);
            return ApiResponse.success(filePath.toString());
        } catch (IOException e) {
            return ApiResponse.error("导出失败: " + e.getMessage());
        }
    }


    @GetMapping("/conversations/{id}")
    public ApiResponse<?> getConversation(@PathVariable Long id) {
        return ApiResponse.success(conversationService.getConversation(id));
    }

}
