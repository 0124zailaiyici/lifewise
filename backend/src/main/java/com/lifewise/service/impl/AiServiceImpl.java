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

        String safetyReply = buildSafetyReply(message, scene);
        if (safetyReply != null) {
            aiCallAuditService.record(userId, "safety-rule", scene + "-triage", scene, "blocked", "[· " + shorten(message, 40) + "] " + safetySourceLabel(scene));
            return new AiReply(safetyReply, "safety-rule", false, safetySourceLabel(scene));
        }

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

    private String buildSafetyReply(String message, String scene) {
        if (!hasText(message)) return null;
        return switch (scene == null ? "" : scene) {
            case "repair" -> buildRepairSafetyReply(message);
            case "health" -> buildHealthSafetyReply(message);
            case "pet" -> buildPetSafetyReply(message);
            default -> null;
        };
    }

    private String buildRepairSafetyReply(String message) {
        String text = message.toLowerCase();
        Map<String, Object> reply = new LinkedHashMap<>();
        reply.put("stage", "安全确认");
        reply.put("status", "建议专业人员");
        reply.put("severity", "严重");
        reply.put("need_professional", true);

        if (containsAny(text, "燃气", "煤气", "gas leak", "燃气味", "煤气味")) {
            reply.put("title", "疑似燃气泄漏，先撤离通风");
            reply.put("problem", "检测到燃气泄漏风险信号");
            reply.put("next_action", "不要开关电器、点火或拨打室内电话；离开室内后联系燃气抢修。仅在无风险时关闭燃气总阀。");
            reply.put("stop_conditions", List.of("闻到持续燃气味", "听到燃气泄漏声", "出现头晕、恶心或人员不适"));
        } else if (containsAny(text, "漏电", "触电", "冒烟", "焦味", "烧焦", "火花", "插座发烫", "电线发烫")) {
            reply.put("title", "存在电气危险，先停止接触");
            reply.put("problem", "检测到触电、过热或起火风险信号");
            reply.put("next_action", "不要触碰设备、插座或积水区域；仅在远离故障点且环境干燥时关闭总电源，然后联系电工或物业。");
            reply.put("stop_conditions", List.of("冒烟、火花或焦味", "插座或电线明显发烫", "设备附近有积水或人员已触电"));
        } else if (containsAny(text, "大量漏水", "喷涌", "爆管", "水管爆", "天花板漏水")) {
            reply.put("title", "大量漏水，先控制水源");
            reply.put("problem", "检测到可能造成财产或电气风险的大量漏水");
            reply.put("next_action", "不要靠近带电设备或积水插座；在安全可达时关闭入户总水阀，并联系物业或水管维修人员。");
            reply.put("stop_conditions", List.of("水接近插座、配电箱或电器", "水流无法用容器控制", "天花板鼓包、开裂或持续滴水"));
        } else {
            return null;
        }

        reply.put("observations", List.of(Map.of("label", "是否有人受伤", "detail", "如有人触电、昏迷或呼吸困难，立即联系急救服务。")));
        reply.put("checklist", List.of(Map.of("item", "现场是否安全", "expected", "确认已远离火源、电源和泄漏点，再联系专业人员。")));
        reply.put("choices", List.of());
        reply.put("tools", List.of());
        reply.put("steps", List.of());
        reply.put("followUps", List.of("我已离开危险区域，下一步联系谁？"));
        try {
            return objectMapper.writeValueAsString(reply);
        } catch (Exception e) {
            log.error("Failed to build repair safety reply", e);
            return null;
        }
    }

    private String buildHealthSafetyReply(String message) {
        String text = message.toLowerCase();
        Map<String, Object> reply = new LinkedHashMap<>();
        reply.put("question", message);
        reply.put("category", "急救知识");
        reply.put("disclaimer", "此回答仅供参考，不能代替专业医疗建议。");
        reply.put("stage", "风险筛查");
        reply.put("status", "紧急就医");
        reply.put("symptoms", List.of());
        reply.put("causes", List.of());
        reply.put("prevention", "");
        reply.put("choices", List.of());
        reply.put("checklist", List.of());
        reply.put("stop_conditions", List.of("出现或加重呼吸困难、意识异常、大量出血或剧烈疼痛时，不要继续居家观察。"));

        if (containsAny(text, "胸痛", "剧烈胸痛", "呼吸困难", "喘不上气", "呼吸急促")) {
            reply.put("next_action", "立即联系急救服务或前往急诊；等待帮助时保持安全体位，不要自行驾车。");
            reply.put("advice", List.of(Map.of("item", "立即寻求急救", "detail", "胸痛或呼吸困难需要尽快由专业人员评估。")));
            reply.put("when_to_see_doctor", "现在就应就医，不要等待症状自行缓解。");
        } else if (containsAny(text, "昏迷", "意识不清", "叫不醒", "抽搐")) {
            reply.put("next_action", "立即联系急救服务；确保周围安全，除非存在立即危险，不要强行移动对方。");
            reply.put("advice", List.of(Map.of("item", "保持呼吸道周围通畅", "detail", "不要把物品塞入口中，也不要强行喂水或喂药。")));
            reply.put("when_to_see_doctor", "现在就应就医，意识异常和抽搐需要紧急评估。");
        } else if (containsAny(text, "严重过敏", "喉咙肿", "嘴唇肿", "脸肿", "全身风团")) {
            reply.put("next_action", "立即联系急救服务或前往急诊；远离可能诱因，不要独自等待症状变化。");
            reply.put("advice", List.of(Map.of("item", "优先处理呼吸风险", "detail", "面部或喉咙肿胀可能快速影响呼吸。")));
            reply.put("when_to_see_doctor", "现在就应就医，特别是伴随喘鸣、头晕或声音变化时。");
        } else if (containsAny(text, "大量出血", "呕血", "咳血", "便血", "高烧不退", "持续高烧", "40度")) {
            reply.put("next_action", "尽快前往急诊或联系急救服务；记录症状开始时间和变化，不要自行加用药物掩盖症状。");
            reply.put("advice", List.of(Map.of("item", "准备就医信息", "detail", "告知专业人员出血或高热的开始时间、频率及伴随症状。")));
            reply.put("when_to_see_doctor", "现在就应就医，不要继续居家观察。");
        } else {
            return null;
        }
        reply.put("observations", List.of(Map.of("label", "症状变化", "detail", "记录开始时间、是否加重，以及是否伴随意识或呼吸变化。")));
        reply.put("followUps", List.of("我已联系急救服务，等待时还需要记录什么？"));
        return toJson(reply, "health safety reply");
    }

    private String buildPetSafetyReply(String message) {
        String text = message.toLowerCase();
        Map<String, Object> reply = new LinkedHashMap<>();
        reply.put("pet_type", "待确认");
        reply.put("topic", "紧急症状分流");
        reply.put("difficulty", "入门");
        reply.put("stage", "风险筛查");
        reply.put("status", "紧急就诊");
        reply.put("choices", List.of());
        reply.put("common_mistakes", List.of("不要喂人用药", "不要强制灌水、催吐或喂食"));
        reply.put("stop_conditions", List.of("呼吸困难、抽搐、误食风险物或无法站立时，不要继续居家观察。"));

        if (containsAny(text, "呼吸困难", "喘不上气", "抽搐", "无法站立", "站不起来")) {
            reply.put("next_action", "立即联系附近宠物医院并尽快出发；保持宠物安静、通风，避免挤压胸腹部。");
            reply.put("steps", List.of(Map.of("step", 1, "action", "用航空箱或平稳垫子转运，减少移动和刺激。", "step_image", "pet carrier transport")));
            reply.put("when_to_see_vet", "现在就应紧急就诊，途中如症状加重，向医院说明情况。");
        } else if (containsAny(text, "误食", "吞了", "异物", "毒物", "巧克力", "老鼠药", "百合")) {
            reply.put("next_action", "立即联系宠物医院，带上误食物包装或拍照；不要自行催吐或灌水。");
            reply.put("steps", List.of(Map.of("step", 1, "action", "记录误食物、估计时间和数量，立即告知宠物医院。", "step_image", "pet poison package")));
            reply.put("when_to_see_vet", "现在就应紧急咨询或就诊，异物和毒物的处理时机很关键。");
        } else if (containsAny(text, "严重外伤", "被车撞", "大出血", "骨折", "咬伤很深")) {
            reply.put("next_action", "立即联系宠物医院；用干净纱布轻压明显出血处，避免自行复位或清理深伤口。");
            reply.put("steps", List.of(Map.of("step", 1, "action", "平稳限制活动后尽快转运，避免触碰疼痛部位。", "step_image", "pet emergency transport")));
            reply.put("when_to_see_vet", "现在就应紧急就诊，外伤可能存在不可见的内部损伤。");
        } else {
            return null;
        }
        reply.put("observations", List.of(Map.of("label", "转运前记录", "detail", "记录宠物种类、体重、症状出现时间，以及是否进食饮水或排泄异常。")));
        reply.put("checklist", List.of(Map.of("item", "联系医院", "expected", "确认医院可接诊并说明紧急症状。")));
        reply.put("followUps", List.of("我已联系宠物医院，转运时需要带什么？"));
        return toJson(reply, "pet safety reply");
    }

    private String toJson(Map<String, Object> reply, String replyName) {
        try {
            return objectMapper.writeValueAsString(reply);
        } catch (Exception e) {
            log.error("Failed to build {}", replyName, e);
            return null;
        }
    }

    private String safetySourceLabel(String scene) {
        return switch (scene == null ? "" : scene) {
            case "repair" -> "修理安全分流";
            case "health" -> "健康紧急分流";
            case "pet" -> "宠物紧急分流";
            default -> "安全分流";
        };
    }

    private boolean containsAny(String text, String... terms) {
        for (String term : terms) {
            if (text.contains(term)) return true;
        }
        return false;
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
            return callOpenAICompatible(visionApiUrl, visionApiKey, visionModel, messages, true, "repair".equals(scene));
        }

        // Always use Qwen via DashScope
        aiCallAuditService.record(userId, "qwen", dashscopeModel, scene, "calling", "[· " + shorten(message, 40) + "] Qwen回答");
        return callOpenAICompatible(dashscopeApiUrl, dashscopeApiKey, dashscopeModel, messages, false, "repair".equals(scene));
    }

    /** 调用 OpenAI 兼容接口（DeepSeek / DashScope Qwen） */
    private String callOpenAICompatible(String url, String key, String modelName,
                                         List<Map<String, Object>> messages, boolean useVision,
                                         boolean forceJsonResponse) throws Exception {
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", modelName);
        requestBody.put("messages", messages);
        requestBody.put("temperature", 0.7);
        requestBody.put("max_tokens", 16384);
        if (forceJsonResponse) {
            requestBody.put("response_format", Map.of("type", "json_object"));
        }

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
        boolean structuredWorkflow = switch (scene == null ? "other" : scene) {
            case "cooking", "shopping", "repair", "housework", "health", "fashion", "etiquette", "pet", "mealplan" -> true;
            default -> false;
        };
        // Guided scenes retain their state fields on follow-up so the card can advance the task.
        if (followUp && !structuredWorkflow) {
            String scenePrompt = scenePromptProperties.getFollowUpPrompt(scene);
            return scenePromptProperties.getFollowUpReturn().replace("{scenePrompt}", scenePrompt);
        }
        String baseRule = scenePromptProperties.getBaseRule();
        String schema = scenePromptProperties.getSchema(scene);
        if (followUp) {
            return baseRule + "\n" + scenePromptProperties.getFollowUpPrompt(scene) + "\n" + schema;
        }
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


