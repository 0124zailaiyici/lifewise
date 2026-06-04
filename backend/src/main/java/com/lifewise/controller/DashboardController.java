package com.lifewise.controller;

import com.lifewise.common.ApiResponse;
import com.lifewise.common.Scene;
import com.lifewise.entity.Conversation;
import com.lifewise.entity.Message;
import com.lifewise.repository.ConversationRepository;
import com.lifewise.repository.MessageRepository;
import com.lifewise.repository.FavoriteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/stats")
@RequiredArgsConstructor
public class DashboardController {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final FavoriteRepository favoriteRepository;

    @GetMapping("/dashboard")
    public ApiResponse<Map<String, Object>> getDashboard(@RequestAttribute Long userId) {
        Map<String, Object> result = new LinkedHashMap<>();

        List<Conversation> allConvs = conversationRepository.findByUserIdOrderByCreatedAtDesc(userId);
        int totalConvs = allConvs.size();

        // 总消息数
        int totalMessages = 0;
        for (Conversation conv : allConvs) {
            totalMessages += messageRepository.findByConversationIdOrderByCreatedAtAsc(conv.getId()).size();
        }

        // 收藏数
        long totalFavorites = favoriteRepository.countByUserId(userId);

        // 场景分布
        List<Map<String, Object>> sceneDist = new ArrayList<>();
        Map<String, Integer> sceneCount = new LinkedHashMap<>();
        for (Conversation conv : allConvs) {
            String s = conv.getScene() != null ? conv.getScene() : "other";
            sceneCount.merge(s, 1, Integer::sum);
        }
        for (Map.Entry<String, Integer> entry : sceneCount.entrySet()) {
            Scene scene = Scene.fromString(entry.getKey());
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("scene", entry.getKey());
            item.put("label", scene.label);
            item.put("icon", scene.icon);
            item.put("count", entry.getValue());
            sceneDist.add(item);
        }

        // 每日趋势（最近30天）
        List<Map<String, Object>> activityData = new ArrayList<>();
        Map<String, Integer> dailyCount = new LinkedHashMap<>();
        LocalDate now = LocalDate.now();
        for (int i = 29; i >= 0; i--) {
            LocalDate date = now.minusDays(i);
            dailyCount.put(date.toString(), 0);
        }
        for (Conversation conv : allConvs) {
            if (conv.getCreatedAt() != null) {
                String dateKey = conv.getCreatedAt().toLocalDate().toString();
                dailyCount.merge(dateKey, 1, Integer::sum);
            }
        }
        for (Map.Entry<String, Integer> entry : dailyCount.entrySet()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("date", entry.getKey());
            item.put("count", entry.getValue());
            activityData.add(item);
        }

        // 今日/本周/总计概览
        Map<String, Object> dailyStats = new LinkedHashMap<>();
        LocalDate today = LocalDate.now();
        LocalDate weekStart = today.minusDays(today.getDayOfWeek().getValue() - 1);

        int todayConvs = 0, todayMsgs = 0, weekConvs = 0, weekMsgs = 0;
        for (Conversation conv : allConvs) {
            if (conv.getCreatedAt() != null) {
                LocalDate cd = conv.getCreatedAt().toLocalDate();
                if (cd.equals(today)) {
                    todayConvs++;
                    todayMsgs += messageRepository.findByConversationIdOrderByCreatedAtAsc(conv.getId()).size();
                }
                if (!cd.isBefore(weekStart)) {
                    weekConvs++;
                    weekMsgs += messageRepository.findByConversationIdOrderByCreatedAtAsc(conv.getId()).size();
                }
            }
        }

        dailyStats.put("today", Map.of("conversations", todayConvs, "messages", todayMsgs));
        dailyStats.put("thisWeek", Map.of("conversations", weekConvs, "messages", weekMsgs));
        dailyStats.put("total", Map.of("conversations", totalConvs, "messages", totalMessages, "favorites", totalFavorites));

        result.put("dailyStats", dailyStats);
        result.put("sceneDistribution", sceneDist);
        result.put("activityData", activityData);

        return ApiResponse.success(result);
    }
}
