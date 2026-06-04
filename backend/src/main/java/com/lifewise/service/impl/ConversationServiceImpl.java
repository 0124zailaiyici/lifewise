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
