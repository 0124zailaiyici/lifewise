package com.lifewise.controller;

import com.lifewise.common.ApiResponse;
import com.lifewise.entity.Conversation;
import com.lifewise.entity.Message;
import com.lifewise.entity.KnowledgeBase;
import com.lifewise.repository.ConversationRepository;
import com.lifewise.repository.MessageRepository;
import com.lifewise.repository.KnowledgeBaseRepository;
import com.lifewise.common.Scene;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class SearchController {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final KnowledgeBaseRepository knowledgeBaseRepository;

    @GetMapping
    public ApiResponse<Map<String, Object>> search(@RequestParam String q,
                                                    @RequestAttribute Long userId) {
        Map<String, Object> result = new LinkedHashMap<>();
        String keyword = "%" + q + "%";

        // 1. 搜索对话
        List<Map<String, Object>> conversations = new ArrayList<>();
        conversationRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
            .filter(c -> c.getTitle() != null && c.getTitle().contains(q))
            .forEach(c -> {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", c.getId());
                item.put("title", c.getTitle());
                item.put("scene", c.getScene());
                Scene scene = Scene.fromString(c.getScene());
                item.put("sceneIcon", scene.icon);
                item.put("sceneLabel", scene.label);
                item.put("type", "conversation");
                conversations.add(item);
            });

        // 2. 搜索消息
        List<Map<String, Object>> messages = new ArrayList<>();
        List<Conversation> userConvs = conversationRepository.findByUserIdOrderByCreatedAtDesc(userId);
        for (Conversation conv : userConvs) {
            List<Message> msgs = messageRepository.findByConversationIdOrderByCreatedAtAsc(conv.getId());
            for (Message msg : msgs) {
                if (msg.getContent() != null && msg.getContent().contains(q)) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", msg.getId());
                    item.put("conversationId", conv.getId());
                    item.put("role", msg.getRole());
                    // 截取包含关键词的片段
                    String content = msg.getContent();
                    int idx = content.indexOf(q);
                    int start = Math.max(0, idx - 30);
                    int end = Math.min(content.length(), idx + q.length() + 30);
                    String snippet = (start > 0 ? "..." : "") + content.substring(start, end) + (end < content.length() ? "..." : "");
                    item.put("snippet", snippet);
                    item.put("convTitle", conv.getTitle());
                    item.put("type", "message");
                    messages.add(item);
                }
            }
        }

        // 3. 搜索知识库
        List<Map<String, Object>> kbResults = new ArrayList<>();
        List<KnowledgeBase> kbList = knowledgeBaseRepository.findByUserIdAndQuestionContaining(userId, q);
        for (KnowledgeBase kb : kbList) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", kb.getId());
            item.put("question", kb.getQuestion());
            item.put("scene", kb.getScene());
            item.put("helpfulCount", kb.getHelpfulCount());
            item.put("type", "knowledge");
            kbResults.add(item);
        }

        result.put("conversations", conversations);
        result.put("messages", messages);
        result.put("knowledge", kbResults);
        result.put("total", conversations.size() + messages.size() + kbResults.size());

        return ApiResponse.success(result);
    }
}
