package com.lifewise.service.impl;

import com.lifewise.common.Scene;
import com.lifewise.dto.*;
import com.lifewise.entity.Conversation;
import com.lifewise.entity.Message;
import com.lifewise.repository.ConversationRepository;
import com.lifewise.repository.MessageRepository;
import com.lifewise.service.ConversationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ConversationServiceImpl implements ConversationService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;

    @Override
    @Transactional
    public ConversationResponse createConversation(Long userId, String title, String scene) {
        Conversation conv = new Conversation();
        conv.setUserId(userId);
        conv.setTitle(title);
        conv.setScene(scene);
        conv = conversationRepository.save(conv);
        return toResponse(conv);
    }

    @Override
    public ConversationResponse getConversation(Long conversationId) {
        Conversation conv = conversationRepository.findById(conversationId)
            .orElseThrow(() -> new RuntimeException("瀵硅瘽涓嶅瓨鍦?"));
        ConversationResponse resp = toResponse(conv);
        List<Message> messages = messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId);
        resp.setMessages(messages.stream().map(this::toMessageResponse).collect(Collectors.toList()));
        return resp;
    }

    @Override
    public List<ConversationResponse> getUserConversations(Long userId) {
        return conversationRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
            .map(this::toResponse)
            .collect(Collectors.toList());
    }

    @Override
    public List<ConversationResponse> getUserConversationsByScene(Long userId, String scene) {
        return conversationRepository.findByUserIdAndSceneOrderByCreatedAtDesc(userId, scene).stream()
            .map(this::toResponse)
            .collect(Collectors.toList());
    }

    private ConversationResponse toResponse(Conversation conv) {
        ConversationResponse r = new ConversationResponse();
        r.setId(conv.getId());
        r.setTitle(conv.getTitle());
        r.setScene(conv.getScene());
        r.setCreatedAt(conv.getCreatedAt());
        Scene s = Scene.fromString(conv.getScene());
        r.setSceneIcon(s.icon);
        r.setSceneLabel(s.label);
        r.setMessages(new ArrayList<>());
        return r;
    }

    @Override
    @Transactional
    public void deleteConversation(Long conversationId, Long userId) {
        Conversation conv = conversationRepository.findById(conversationId)
            .orElseThrow(() -> new RuntimeException("对话不存在"));
        if (!conv.getUserId().equals(userId)) {
            throw new RuntimeException("无权删除此对话");
        }
        messageRepository.deleteByConversationId(conversationId);
        conversationRepository.delete(conv);
    }

    @Override
    @Transactional
    public ConversationResponse renameConversation(Long conversationId, Long userId, String newTitle) {
        Conversation conv = conversationRepository.findById(conversationId)
            .orElseThrow(() -> new RuntimeException("对话不存在"));
        if (!conv.getUserId().equals(userId)) {
            throw new RuntimeException("无权修改此对话");
        }
        conv.setTitle(newTitle);
        conv = conversationRepository.save(conv);
        return toResponse(conv);
    }


    @Override
    @Transactional
    public int repairBadTitles(Long userId) {
        List<Conversation> conversations = conversationRepository.findByUserIdOrderByCreatedAtDesc(userId);
        int count = 0;
        for (Conversation conv : conversations) {
            if (isBadTitle(conv.getTitle())) {
                conv.setTitle(sceneTitle(conv.getScene()));
                conversationRepository.save(conv);
                count++;
            }
        }
        return count;
    }

    private boolean isBadTitle(String title) {
        if (title == null || title.trim().isEmpty()) return true;
        String trimmed = title.trim();
        long questionMarks = trimmed.chars().filter(ch -> ch == '?').count();
        if (questionMarks >= 3) return true;
        if (trimmed.indexOf('\uFFFD') >= 0 || trimmed.indexOf('\u951F') >= 0) return true;
        String[] mojibakeMarkers = {"\u940E", "\u934B", "\u7035", "\u7487", "\u93B8", "\u6D93", "\u951B"};
        for (String marker : mojibakeMarkers) {
            if (trimmed.contains(marker)) return true;
        }
        return false;
    }

    private String sceneTitle(String scene) {
        return switch (Scene.fromString(scene)) {
            case cooking -> "\u505a\u996d\u5bf9\u8bdd";
            case shopping -> "\u4e70\u83dc\u5bf9\u8bdd";
            case repair -> "\u4fee\u7406\u5bf9\u8bdd";
            case housework -> "\u5bb6\u52a1\u5bf9\u8bdd";
            case health -> "\u5065\u5eb7\u5bf9\u8bdd";
            case fashion -> "\u7a7f\u642d\u5bf9\u8bdd";
            case etiquette -> "\u793c\u4eea\u5bf9\u8bdd";
            case pet -> "\u5ba0\u7269\u5bf9\u8bdd";
            case writing -> "\u5199\u4f5c\u5bf9\u8bdd";
            case mealplan -> "\u98df\u8c31\u5bf9\u8bdd";
            case other -> "\u751f\u6d3b\u5bf9\u8bdd";
        };
    }

    private MessageResponse toMessageResponse(Message msg) {
        MessageResponse r = new MessageResponse();
        r.setId(msg.getId());
        r.setRole(msg.getRole());
        r.setContent(msg.getContent());
        r.setImageUrl(msg.getImageUrl());
        r.setCreatedAt(msg.getCreatedAt());
        return r;
    }
}
