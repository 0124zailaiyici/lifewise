package com.lifewise.service.impl;

import com.lifewise.dto.ChatRequest;
import com.lifewise.dto.AiReply;
import com.lifewise.entity.Message;
import com.lifewise.repository.MessageRepository;
import com.lifewise.service.AiCallAuditService;
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
    private final AiCallAuditService aiCallAuditService;
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${ai.api-url}")
    private String apiUrl;

    @Value("${ai.api-key}")
    private String apiKey;

    @Value("${ai.model:deepseek-chat}")
    private String model;

    // deepseek removed (always qwen)

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

    // DashScope (Qwen) 配置
    @Value("${ai.dashscope-api-url:https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions}")
    private String dashscopeApiUrl;

    @Value("${ai.dashscope-api-key:}")
    private String dashscopeApiKey;

    @Value("${ai.dashscope-model:qwen-plus}")
    private String dashscopeModel;

    // Ollama 配置
    @Value("${ai.ollama-url:http://localhost:11434/api/chat}")
    private String ollamaUrl;

    @Value("${ai.ollama-model:qwen2.5:7b}")
    private String ollamaModel;

    @Override
    public String chat(String message, String scene, Long userId, Long conversationId, String imageUrl) {
        // 兼容旧接口
        ChatRequest req = new ChatRequest();
        req.setMessage(message);
        req.setScene(scene);
        req.setConversationId(conversationId);
        req.setImageUrl(imageUrl);
        req.setProvider("qwen");
        return chat(req, userId);
    }

    @Override
    public String chat(ChatRequest request, Long userId) {
        return chatWithMetadata(request, userId).getContent();
    }

    @Override
    public AiReply chatWithMetadata(ChatRequest request, Long userId) {
        String message = request.getMessage();
        String scene = request.getScene();
        Long conversationId = request.getConversationId();
        String imageUrl = request.getImageUrl();
        String provider = request.getProvider() != null ? request.getProvider() : "qwen";
        boolean followUp = Boolean.TRUE.equals(request.getFollowUp());

        // Skip cache for explicit follow-up chips or image questions.
        if (!followUp && (imageUrl == null || imageUrl.isEmpty())) {
            String cached = knowledgeBaseService.findAnswer(message, scene, userId);
            if (cached != null) {
                log.info("cache hit: {}", message);
                aiCallAuditService.record(userId, "cache", "knowledge-base", scene, "hit", "Knowledge base cache hit; no external AI call");
                return new AiReply(cached, "knowledge-base", false, "来自常识库，未调用 AI");
            }
        } else {
            log.debug("skip cache (explicit follow-up or image): {}", message);
        }

        String answer = callAI(message, scene, conversationId, imageUrl, provider, userId, followUp);

        // Cache normal text questions, but skip explicit follow-up chips.
        if (!followUp && !hasText(imageUrl) && answer != null && !answer.contains("Mock response") && !answer.contains("configure API key")) {
            knowledgeBaseService.saveAnswer(message, answer, scene, userId);
        } else {
            log.debug("Skipping cache for mock/error response");
        }
        String source = hasText(imageUrl) ? "vision" : provider;
        boolean externalCall = isExternalProviderCall(source, answer);
        return new AiReply(answer, source, externalCall, sourceLabel(source, externalCall));
    }

    private boolean isExternalProviderCall(String provider, String answer) {
        if ("ollama".equals(provider)) return false;
        if (answer == null) return false;
        String lower = answer.toLowerCase();
        return !lower.contains("api key is not configured")
            && !lower.contains("api key is missing")
            && !lower.contains("disabled by server cost guard")
            && !lower.contains("mock response")
            && !answer.contains("未配置")
            && !answer.contains("未调用");
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String sourceLabel(String provider, boolean externalCall) {
        if ("ollama".equals(provider)) return "来自 Ollama 本地模型，未调用外部 AI";
        if (!externalCall) return "未调用外部 AI";
        if ("qwen".equals(provider)) return "来自千问 Qwen";
        if ("vision".equals(provider)) return "来自图片识别模型";
        if ("deepseek".equals(provider)) return "来自 DeepSeek";
        return "来自 AI";
    }

    private String callAI(String message, String scene, Long conversationId, String imageUrl, String provider, Long userId, boolean followUp) {

        if (hasText(imageUrl) && (!visionEnabled || !hasText(visionApiUrl) || !hasText(visionApiKey))) {
            log.warn("Vision API not configured for image chat");
            aiCallAuditService.record(userId, "vision", visionModel, scene, "blocked", "Vision API key/url missing; no external API call");
            return "{\"answer\":\"图片识别未配置。请在服务器配置 ai.vision-api-url、ai.vision-api-key，并确认 app.vision-enabled=true。\",\"tips\":[\"图片已上传，但当前后端不能识别图片内容\",\"配置完成后重启后端再试\"]}";
        }
        if ("qwen".equals(provider) && (dashscopeApiKey == null || dashscopeApiKey.isEmpty())) {
            log.warn("DashScope API key not configured for Qwen, please check server config");
            aiCallAuditService.record(userId, "qwen", dashscopeModel, scene, "blocked", "Qwen API key missing; no external API call");
            return "{\"answer\":\"Qwen API key is not configured. Please set ai.dashscope-api-key on the server, or switch to Ollama.\",\"tips\":[\"Go to Profile -> AI Model to switch provider\"]}";
        }

        // 最多重试 2 次（共 3 次尝试）
        Exception lastEx = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                return callLLMApi(message, scene, conversationId, imageUrl, provider, userId, followUp);
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
    private String callLLMApi(String message, String scene, Long conversationId, String imageUrl, String provider, Long userId, boolean followUp) throws Exception {
        List<Map<String, Object>> messages = new ArrayList<>();
        boolean hasImage = imageUrl != null && !imageUrl.isEmpty();
        Map<String, Object> systemMsg = new LinkedHashMap<>();
        systemMsg.put("role", "system");
        systemMsg.put("content", buildSystemPrompt(scene, hasImage, followUp));
        messages.add(systemMsg);

        if (conversationId != null) {
            List<Message> history = messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId);
            int startIdx = Math.max(0, history.size() - 5);
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

        // Image questions must use the configured vision model; text-only provider selection stays unchanged.
        if (hasImage) {
            aiCallAuditService.record(userId, "vision", visionModel, scene, "calling", "Calling vision API for image chat");
            return callOpenAICompatible(visionApiUrl, visionApiKey, visionModel, messages, true);
        }

        // Always use Qwen via DashScope
        aiCallAuditService.record(userId, "qwen", dashscopeModel, scene, "calling", "Calling DashScope Qwen");
        return callOpenAICompatible(dashscopeApiUrl, dashscopeApiKey, dashscopeModel, messages, false);
    }

    /** 调用 OpenAI 兼容接口（DeepSeek / DashScope Qwen） */
    private String callOpenAICompatible(String url, String key, String modelName,
                                         List<Map<String, Object>> messages, boolean useVision) throws Exception {
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", modelName);
        requestBody.put("messages", messages);
        requestBody.put("temperature", 0.7);
        requestBody.put("max_tokens", 4096);

        String body = objectMapper.writeValueAsString(requestBody);
        log.debug("AI request to {}: {}", url, body.substring(0, Math.min(200, body.length())));

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + key)
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            log.error("API error: {} - {}", response.statusCode(), response.body());
            throw new RuntimeException("API error: " + response.statusCode() + " - " + response.body());
        }

        JsonNode root = objectMapper.readTree(response.body());
        String result = root.path("choices").path(0).path("message").path("content").asText();
        String finishReason = root.path("choices").path(0).path("finish_reason").asText("");
        log.debug("AI finish_reason: {}, content_len: {}", finishReason, result.length());
        return result;
    }

    /** 调用 Ollama 本地 API */
    

    /** 将本地图片路径转为 base64 data URL */
    private String imageToDataUrl(String imageUrl) {
        try {
            if (imageUrl == null || imageUrl.isEmpty()) return "";
            if (imageUrl.startsWith("http")) return imageUrl;
            log.debug("imageToDataUrl() called with: {}", imageUrl);
            // 本地文件路径：/uploads/20260604/uuid.jpg
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

    private String buildSystemPrompt(String scene, boolean hasImage, boolean followUp) {
        // Follow-up mode: only use natural language when frontend explicitly marks a clicked follow-up chip.
        if (followUp) {
            String scenePrompt;
            switch (scene != null ? scene : "other") {
                case "cooking":
                    scenePrompt = "用户正在当前对话基础上追问食材替代、口味调整、烹饪技巧等，请保持烹饪菜谱风格回答";
                    break;
                case "shopping":
                    scenePrompt = "用户正在当前对话基础上追问挑选细节、保存方法、应季品种等，请保持选购指南风格回答";
                    break;
                case "repair":
                    scenePrompt = "用户正在当前对话基础上追问修理细节、工具替代、安全注意等，请保持修理指南风格回答";
                    break;
                case "housework":
                    scenePrompt = "用户正在当前对话基础上追问清洁技巧、材料替代、注意事项等，请保持家务技巧风格回答";
                    break;
                case "health":
                    scenePrompt = "用户正在当前对话基础上追问症状细节、用药建议、就医时机等，请保持健康常识风格回答";
                    break;
                case "fashion":
                    scenePrompt = "用户正在当前对话基础上追问搭配细节、颜色选择、场合建议等，请保持穿搭指南风格回答";
                    break;
                case "etiquette":
                    scenePrompt = "用户正在当前对话基础上追问礼仪细节、场合差异、文化说明等，请保持社交礼仪风格回答";
                    break;
                case "pet":
                    scenePrompt = "用户正在当前对话基础上追问照料细节、常见问题、就医判断等，请保持宠物照料风格回答";
                    break;
                default:
                    scenePrompt = "用户正在当前对话基础上追问细节、补充信息等，请保持之前的回答风格";
                    break;
            }
            return """
你是 LifeWise 生活助手，专门帮助缺乏生活经验的新手。回答要通俗易懂，步骤要具体可操作。

""" + scenePrompt + """
保持口语化、亲切感，像朋友聊天一样。不要输出 JSON 格式，不要结构化卡片。
""";
        }
        String baseRule = """
你是 LifeWise 生活助手，专门帮助缺乏生活经验的新手。回答要通俗易懂，步骤要具体可操作。涉及危险必须提醒。只输出纯JSON，不要markdown标记。如果用户上传了图片，优先分析图片内容。
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
- selection_steps: 挑选步骤，每个对象包含 step_name（步骤名称）、action（具体操作描述）、step_image（步骤配图英文关键词如"checking watermelon"）
- common_mistakes: 常见误区列表
- storage_tip: 保存方法
- summary_slogan: 总结口诀

示例：
{"category":"西瓜","season":"夏季应季","selection_steps":[{"step_name":"看外观","action":"选深绿带光泽、纹路清晰均匀的；瓜底部凹陷深、圆圈小的皮薄肉甜"},{"step_name":"听声音","action":"指关节轻敲，声音低沉浑厚像敲鼓为好"}],"common_mistakes":["不要只看瓜藤是否弯曲，关键是颜色和鲜活性"],"storage_tip":"常温阴凉处保存，切开后冷藏","summary_slogan":"一看二摸三听四掂，凹陷深、藤新鲜就是好瓜"}
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
- steps: 修理步骤，每个对象包含 step（序号）、action（操作描述）、warning（安全提醒，可选）、step_image（步骤配图英文关键词如"turning off valve"）
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
- steps: 操作步骤列表，每个对象包含 step（序号）、action（操作描述）、step_image（步骤配图英文关键词如"cleaning stain"）
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
- suggestions: 建议列表，每个对象包含 item（建议项）、detail（详细说明）、step_image（配图英文关键词如"fever thermometer"）
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
- outfits: 推荐穿搭列表，每个对象包含 piece（单品）、description（款式建议）、color（推荐颜色）、step_image（商品配图英文关键词如"white tshirt"）
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
- steps: 照料步骤列表，每个对象包含 step（序号）、action（操作描述）、step_image（步骤配图英文关键词如"brushing dog"）
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
- weekly_plan: 一周计划，每天包含 day（星期几）、meals（三餐列表，每餐含type、name、time、difficulty）
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
- 直接输出内容，不要 JSON 包装
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


