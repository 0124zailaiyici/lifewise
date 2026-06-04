package com.lifewise.controller;

import com.lifewise.common.ApiResponse;
import com.lifewise.dto.FavoriteResponse;
import com.lifewise.entity.Favorite;
import com.lifewise.entity.Message;
import com.lifewise.entity.Conversation;
import com.lifewise.repository.FavoriteRepository;
import com.lifewise.repository.MessageRepository;
import com.lifewise.repository.ConversationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/favorites")
@RequiredArgsConstructor
public class FavoriteController {

    private final FavoriteRepository favoriteRepository;
    private final MessageRepository messageRepository;
    private final ConversationRepository conversationRepository;

    @PostMapping
    public ApiResponse<?> addFavorite(@RequestHeader Long userId,
                                       @RequestParam Long messageId,
                                       @RequestParam(required = false) String note) {
        if (favoriteRepository.existsByUserIdAndMessageId(userId, messageId)) {
            return ApiResponse.error("已收藏过该内容");
        }
        Favorite fav = new Favorite();
        fav.setUserId(userId);
        fav.setMessageId(messageId);
        fav.setNote(note);
        favoriteRepository.save(fav);
        return ApiResponse.success("收藏成功");
    }

    @DeleteMapping
    public ApiResponse<?> removeFavorite(@RequestHeader Long userId,
                                          @RequestParam Long messageId) {
        favoriteRepository.deleteByUserIdAndMessageId(userId, messageId);
        return ApiResponse.success("取消收藏");
    }

    @GetMapping
    public ApiResponse<List<FavoriteResponse>> getFavorites(@RequestHeader Long userId) {
        List<Favorite> list = favoriteRepository.findByUserIdOrderByCreatedAtDesc(userId);
        List<FavoriteResponse> result = new ArrayList<>();

        for (Favorite fav : list) {
            FavoriteResponse resp = new FavoriteResponse();
            resp.setId(fav.getId());
            resp.setMessageId(fav.getMessageId());
            resp.setNote(fav.getNote());
            resp.setCreatedAt(fav.getCreatedAt());

            // 获取消息内容作为摘要
            Message msg = messageRepository.findById(fav.getMessageId()).orElse(null);
            if (msg != null) {
                resp.setConversationId(msg.getConversationId());
                String content = msg.getContent();
                if (content != null && !content.isEmpty()) {
                    if (content.startsWith("{")) {
                        try {
                            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                            com.fasterxml.jackson.databind.JsonNode node = mapper.readTree(content);
                            if (node.has("title")) {
                                resp.setSummary(node.get("title").asText());
                            } else if (node.has("品类")) {
                                resp.setSummary(node.get("品类").asText() + " 挑选指南");
                            } else {
                                resp.setSummary(content.substring(0, Math.min(30, content.length())) + "...");
                            }
                        } catch (Exception e) {
                            resp.setSummary(content.substring(0, Math.min(30, content.length())) + "...");
                        }
                    } else {
                        resp.setSummary(content.substring(0, Math.min(30, content.length())) + "...");
                    }
                }

                // 获取场景
                Conversation conv = conversationRepository.findById(msg.getConversationId()).orElse(null);
                if (conv != null) {
                    resp.setScene(conv.getScene());
                }
            }

            result.add(resp);
        }

        return ApiResponse.success(result);
    }
}
