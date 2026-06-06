package com.lifewise.service.impl;

import com.lifewise.entity.Message;
import com.lifewise.repository.MessageRepository;
import com.lifewise.service.AiService;
import com.lifewise.service.KnowledgeBaseService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiServiceImpl implements AiService {

    private final KnowledgeBaseService knowledgeBaseService;
    private final MessageRepository messageRepository;
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${ai.api-url}")
    private String apiUrl;

    @Value("${ai.api-key}")
    private String apiKey;

    @Value("${ai.model:deepseek-chat}")
    private String model;

    @Value("${ai.vision-api-url:}")
    private String visionApiUrl;

    @Value("${ai.vision-api-key:}")
    private String visionApiKey;

    @Value("${ai.vision-model:mimo-v2-omni}")
    private String visionModel;

    @Value("${app.upload-dir:./uploads}")
    private String uploadDir;

    @Value("${app.vision-enabled:false}")
    private boolean visionEnabled;

    @Override
    public String chat(String message, String scene, Long userId, Long conversationId, String imageUrl) {
        // Skip cache when image is present (image analysis should always use AI)
        if (imageUrl == null || imageUrl.isEmpty()) {
            String cached = knowledgeBaseService.findAnswer(message, scene);
            if (cached != null) {
                log.info("cache hit: {}", message);
                return cached;
            }
        } else {
            log.debug("skip cache for image request: {}", message);
        }
        String answer = callAI(message, scene, conversationId, imageUrl);
        // Only cache if it looks like a real response (not mock, not error)
        if (answer != null && !answer.contains("Mock response") && !answer.contains("configure API key")) {
            knowledgeBaseService.saveAnswer(message, answer, scene);
        } else {
            log.debug("Skipping cache for mock/error response");
        }
        return answer;
    }

    private String callAI(String message, String scene, Long conversationId, String imageUrl) {
        if (apiKey == null || apiKey.isEmpty()) {
            log.warn("no api key, use mock");
            return mockResponse(message, scene);
        }
        // 最多重试 2 次（共 3 次尝试）
        Exception lastEx = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                return callLLMApi(message, scene, conversationId, imageUrl);
            } catch (Exception e) {
                lastEx = e;
                log.warn("AI call failed (attempt {}/3): {}", attempt, e.getMessage());
                if (attempt < 3) {
                    try { Thread.sleep(1000 * attempt); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
                }
            }
        }
        log.error("AI call failed after 3 attempts: {}", lastEx.getMessage(), lastEx);
        return mockResponse(message, scene);
    }

    @SuppressWarnings("unchecked")
    private String callLLMApi(String message, String scene, Long conversationId, String imageUrl) throws Exception {
        List<Map<String, Object>> messages = new ArrayList<>();
        boolean hasImage = imageUrl != null && !imageUrl.isEmpty();

        Map<String, Object> systemMsg = new LinkedHashMap<>();
        systemMsg.put("role", "system");
        systemMsg.put("content", buildSystemPrompt(scene, hasImage));
        messages.add(systemMsg);

        if (conversationId != null) {
            List<Message> history = messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId);
            int startIdx = Math.max(0, history.size() - 10);
            for (int i = startIdx; i < history.size(); i++) {
                Message msg = history.get(i);
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("role", msg.getRole());
                if (visionEnabled && msg.getImageUrl() != null && !msg.getImageUrl().isEmpty()) {
                    List<Map<String, Object>> contentList = new ArrayList<>();
                    if (msg.getContent() != null && !msg.getContent().isEmpty()) {
                        Map<String, Object> textPart = new LinkedHashMap<>();
                        textPart.put("type", "text");
                        textPart.put("text", msg.getContent());
                        contentList.add(textPart);
                    }
                    Map<String, Object> imagePart = new LinkedHashMap<>();
                    imagePart.put("type", "image_url");
                    Map<String, Object> imageUrlObj = new LinkedHashMap<>();
                    String imgDataUrl = imageToDataUrl(msg.getImageUrl());
                    imageUrlObj.put("url", imgDataUrl);
                    imagePart.put("image_url", imageUrlObj);
                    contentList.add(imagePart);
                    m.put("content", contentList);
                } else {
                    m.put("content", msg.getContent() != null ? msg.getContent() : "");
                }
                messages.add(m);
            }
        }

        Map<String, Object> userMsg = new LinkedHashMap<>();
        userMsg.put("role", "user");
        if (imageUrl != null && !imageUrl.isEmpty() && visionEnabled && visionApiUrl != null && !visionApiUrl.isEmpty()) {
            List<Map<String, Object>> contentList = new ArrayList<>();
            Map<String, Object> textPart = new LinkedHashMap<>();
            textPart.put("type", "text");
            textPart.put("text", message);
            contentList.add(textPart);
            Map<String, Object> imagePart = new LinkedHashMap<>();
            imagePart.put("type", "image_url");
            Map<String, Object> imageUrlObj = new LinkedHashMap<>();
            String imgDataUrl = imageToDataUrl(imageUrl);
            imageUrlObj.put("url", imgDataUrl);
            imagePart.put("image_url", imageUrlObj);
            contentList.add(imagePart);
            userMsg.put("content", contentList);
        } else {
            userMsg.put("content", message);
        }
        messages.add(userMsg);

        boolean useVision = hasImage && visionEnabled && visionApiUrl != null && !visionApiUrl.isEmpty();
        String targetUrl = useVision ? visionApiUrl : apiUrl;
        String targetModel = useVision ? visionModel : model;

        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", targetModel);
        requestBody.put("messages", messages);
        requestBody.put("temperature", 0.7);
        requestBody.put("max_tokens", 4096);

        String body = objectMapper.writeValueAsString(requestBody);
        log.debug("AI request: {}", body);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(targetUrl))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + (useVision ? visionApiKey : apiKey))
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            log.error("API error: {} - {}", response.statusCode(), response.body());
            throw new RuntimeException("API error: " + response.statusCode() + " - " + response.body());
        }

        JsonNode root = objectMapper.readTree(response.body());
        String result = root.path("choices").path(0).path("message").path("content").asText(); String finishReason = root.path("choices").path(0).path("finish_reason").asText(""); log.debug("AI finish_reason: {}, content_len: {}", finishReason, result.length());
        log.debug("AI response: {}", result);
        return result;
    }

    /** 将本地图片路径转为 base64 data URL */
    private String imageToDataUrl(String imageUrl) {
        try {
            if (imageUrl == null || imageUrl.isEmpty()) return "";
            if (imageUrl.startsWith("http")) return imageUrl;
            log.debug("imageToDataUrl() called with: {}", imageUrl);
            // 本地文件路径： /uploads/20260604/uuid.jpg
            String filePath = uploadDir + imageUrl.replace("/uploads", "").replace("/", "\\");
            Path path = Paths.get(uploadDir, imageUrl.replace("/uploads", "").replace("/", "\\").replace("\\", "/"));
            // Try more robust path resolution
            String relativePath = imageUrl.startsWith("/") ? imageUrl.substring(1) : imageUrl;
            if (relativePath.startsWith("uploads/")) {
                relativePath = relativePath.substring("uploads/".length());
            }
            Path fullPath = Paths.get(uploadDir).resolve(relativePath).normalize();
            byte[] bytes = java.nio.file.Files.readAllBytes(fullPath);
            String mime = "image/jpeg";
            if (imageUrl.endsWith(".png")) mime = "image/png";
            else if (imageUrl.endsWith(".gif")) mime = "image/gif";
            else if (imageUrl.endsWith(".webp")) mime = "image/webp";
            return "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(bytes);
        } catch (Exception e) {
            log.warn("Failed to read image: {}", imageUrl, e);
            return imageUrl;
        }
    }

    private String buildSystemPrompt(String scene, boolean hasImage) {
        String baseRule = """
你是 LifeWise 生活助手，专门帮助缺乏生活经验的新手。回答要通俗易懂，步骤要具体可操作。涉及危险必须提醒。只输出纯 JSON，不要 markdown 标记。如果用户上传了图片，优先分析图片内容。
""";
        String schema;
        switch (scene != null ? scene : "other") {
            case "cooking":
                schema = """
输出菜谱JSON，字段：
title(菜名，必须具体，如"西红柿炒鸡蛋")
difficulty(入门/初级/中级)
time(用时)
servings(份量)
ingredients(数组，每项name食材,amount用量,note备注)
steps(数组，每项step序号,action操作,tip提示,step_image步骤配图英文关键词如"cutting tomatoes")
tips(提醒)
key_point(关键)
followUps(推荐追问列表，数组，如["追问1","追问2","追问3"])

示例：{"title":"西红柿炒鸡蛋","difficulty":"入门","time":"15分钟","servings":"2人份","ingredients":[{"name":"西红柿","amount":"2个","note":"选熟透的"},{"name":"鸡蛋","amount":"3个"}],"steps":[{"step":1,"action":"西红柿切块，鸡蛋打散加少许盐","step_image":"cutting tomatoes"},{"step":2,"action":"热锅倒油，倒入蛋液炒至凝固盛出","step_image":"frying eggs"},{"step":3,"action":"锅中加油炒西红柿出汁，倒回鸡蛋翻炒均匀","step_image":"stir frying"}],"tips":"全程大火快炒","key_point":"西红柿要炒出红油再和鸡蛋混合"}
直接输出JSON，不要```标记。
""";
                break;
            case "shopping":
                schema = """
### 场景：买菜/水果挑选指南
输出 JSON 格式，字段说明：
- category: 品类名称
- season: 当前是否应季
- selection_steps: 挑选步骤，每个对象包含 step_name（步骤名称）、action（具体操作描述）
- common_mistakes: 常见误区列表
- storage_tip: 保存方法
- summary_slogan: 总结口诀

示例：
{"category":"西瓜","season":"夏季应季","selection_steps":[{"step_name":"看外观","action":"选深绿带光泽、纹路清晰均匀的；瓜底部凹陷深、圆圈小的皮薄肉甜"},{"step_name":"听声音","action":"指关节轻敲，声音低沉浑厚像敲鼓为好"}],"common_mistakes":["不要只看瓜蒂是否弯曲，关键是颜色和鲜活度"],"storage_tip":"常温阴凉处保存，切开后冷藏","summary_slogan":"一看二摸三听四掂，凹陷深、藤新鲜就是好瓜"}
""";
                break;
            case "repair":
                schema = """
### 场景：家庭修理指南
输出 JSON 格式，字段说明：
- problem: 问题描述
- severity: 严重程度（轻微/中等/严重）
- need_professional: 是否需要请专业人员（true/false）
- tools: 所需工具列表
- steps: 修理步骤，每个对象包含 step（序号）、action（操作描述）、warning（安全提醒，可选）
- professional_advice: 什么情况下建议找专业人士
""";
                break;
            case "housework":
                schema = """
### 场景：家务技巧
输出 JSON 格式，字段说明：
- problem: 问题描述
- difficulty: 难度（简单/中等/困难）
- materials: 所需材料列表，每个对象包含 name（材料名）、alternative（替代品，可选）
- steps: 操作步骤列表
- safety_tip: 安全提示
- prevention: 如何预防此类问题
""";
                break;
            case "health":
                schema = """
### 场景：健康常识
输出 JSON 格式，字段说明：
- question: 问题描述
- category: 分类（症状处理/用药常识/营养建议/急救知识）
- disclaimer: 免责声明（此回答仅供参考，不能代替专业医疗建议）
- suggestions: 建议列表，每个对象包含 item（建议项）、detail（详细说明）
- when_to_see_doctor: 什么情况下必须去看医生
- prevention: 日常预防措施
""";
                break;
            case "fashion":
                schema = """
### 场景：穿搭指南
输出 JSON 格式，字段说明：
- occasion: 场合
- style: 推荐风格
- color_palette: 推荐颜色列表
- outfits: 推荐穿搭列表，每个对象包含 piece（单品）、description（款式建议）、color（推荐颜色）
- avoid: 避免什么
- tips: 穿搭小技巧
""";
                break;
            case "etiquette":
                schema = """
### 场景：社交礼仪
输出 JSON 格式，字段说明：
- occasion: 场合
- key_principles: 关键原则列表
- do_list: 应该做的事列表，每个对象包含 action（应该做的事）、reason（为什么要这样做）
- dont_list: 不应该做的事列表
- cultural_notes: 文化差异说明（如适用）
""";
                break;
            case "pet":
                schema = """
### 场景：宠物照料
输出 JSON 格式，字段说明：
- pet_type: 宠物类型
- topic: 问题主题
- difficulty: 难度（入门/初级/中级）
- steps: 照料步骤列表
- common_mistakes: 常见错误列表
- when_to_see_vet: 什么情况下需要看兽医
""";
                break;
            case "mealplan":
                schema = """
### 场景：食谱规划
根据用户提供的食材、口味偏好、预算等信息，推荐一周的每日食谱。
输出 JSON 格式：
- title: 标题
- preference: 用户需求概述
- weekly_plan: 一周计划，每天包含 day（星期几）、meals（三餐列表，每餐含 type、name、time、difficulty）
- shopping_list: 购物清单，按分类列出
- tips: 省时省钱建议
""";
                break;
            case "writing":
                schema = """
### 场景：写作助手
帮助用户写日记、周记、备忘录、待办清单、学习笔记、灵感记录等。

支持以下写作类型（根据用户需求自动选择最合适的格式）：
- 日记/周记：第一人称，时间顺序，含日期天气心情事件感悟
- 待办清单：分类列出，每项含任务和优先级
- 备忘录：主题明确，结构清晰，关键信息突出
- 学习笔记：核心概念、关键要点、个人理解
- 购物清单：按类别分组，简洁条目
- 灵感/想法记录：自由形式，标题加描述

输出要求：
- 使用 Markdown 格式，标题用 ## 或 ###，列表用 - 或 1.
- 重点内容用 **加粗**
- 直接输出内容，不要 JSON 包裹
""";
                break;
            default:
                schema = """
### 通用模式
输出 JSON 格式：
- question: 用户问题
- answer: 详细回答
- tips: 小贴士列表
""";
                break;
        }
        return baseRule + "\n" + schema;
    }

    private String mockResponse(String message, String scene) {
        return "{\"question\":\"" + message.replace("\"", "\\\"") + "\",\"answer\":\"Mock response. API key not configured.\",\"tips\":[\"Configure API key in settings\"]}";
    }
}



