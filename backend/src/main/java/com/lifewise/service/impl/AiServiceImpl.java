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
import com.lifewise.config.ScenePromptProperties;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiServiceImpl implements AiService {

    private final KnowledgeBaseService knowledgeBaseService;
    private final MessageRepository messageRepository;
    private final AiCallAuditService aiCallAuditService;
    private final ScenePromptProperties scenePromptProperties;
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
                aiCallAuditService.record(userId, "cache", "knowledge-base", scene, "hit", "[· " + shorten(message, 40) + "] KB缓存命中");
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
            aiCallAuditService.record(userId, "vision", visionModel, scene, "blocked", "[· " + shorten(message, 40) + "] 视觉未配置");
            return "{\"answer\":\"图片识别未配置。请在服务器配置 ai.vision-api-url、ai.vision-api-key，并确认 app.vision-enabled=true。\",\"tips\":[\"图片已上传，但当前后端不能识别图片内容\",\"配置完成后重启后端再试\"]}";
        }
        if ("qwen".equals(provider) && (dashscopeApiKey == null || dashscopeApiKey.isEmpty())) {
            log.warn("DashScope API key not configured for Qwen, please check server config");
            aiCallAuditService.record(userId, "qwen", dashscopeModel, scene, "blocked", "[· " + shorten(message, 40) + "] Qwen Key未配置");
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
            aiCallAuditService.record(userId, "vision", visionModel, scene, "calling", "[· " + shorten(message, 40) + "] 视觉回答");
            return callOpenAICompatible(visionApiUrl, visionApiKey, visionModel, messages, true);
        }

        // Always use Qwen via DashScope
        aiCallAuditService.record(userId, "qwen", dashscopeModel, scene, "calling", "[· " + shorten(message, 40) + "] Qwen回答");
        return callOpenAICompatible(dashscopeApiUrl, dashscopeApiKey, dashscopeModel, messages, false);
    }

    /** 调用 OpenAI 兼容接口（DeepSeek / DashScope Qwen） */
    private String callOpenAICompatible(String url, String key, String modelName,
                                         List<Map<String, Object>> messages, boolean useVision) throws Exception {
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", modelName);
        requestBody.put("messages", messages);
        requestBody.put("temperature", 0.7);
        requestBody.put("max_tokens", 16384);

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
        // Follow-up mode: natural language when frontend explicitly marks a clicked follow-up chip.
        if (followUp) {
            String scenePrompt = scenePromptProperties.getFollowUpPrompt(scene);
            return scenePromptProperties.getFollowUpReturn().replace("{scenePrompt}", scenePrompt);
        }
        String baseRule = scenePromptProperties.getBaseRule();
        String schema = scenePromptProperties.getSchema(scene);
        return baseRule + "\n" + schema;
    }

    private String shorten(String s, int max) {
        if (s == null) return "";
        String v = s.replaceAll("[\\s\\u3000]+", " ").trim();
        if (v.length() <= max) return v;
        return v.substring(0, max) + "...";
    }

    private String mockResponse(String message, String scene) {
        return "{\"question\":\"" + message.replace("\"", "\\\"") + "\",\"answer\":\"Mock response. API key not configured.\",\"tips\":[\"Configure API key in settings\"]}";
    }
}


