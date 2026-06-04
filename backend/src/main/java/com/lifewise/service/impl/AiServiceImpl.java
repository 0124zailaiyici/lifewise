package com.lifewise.service.impl;

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

@Slf4j
@Service
@RequiredArgsConstructor
public class AiServiceImpl implements AiService {

    private final KnowledgeBaseService knowledgeBaseService;
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${ai.api-url}")
    private String apiUrl;

    @Value("${ai.api-key}")
    private String apiKey;

    @Value("${ai.model:deepseek-chat}")
    private String model;

    @Override
    public String chat(String message, String scene, Long userId) {
        // 1. 先查知识库
        String cached = knowledgeBaseService.findAnswer(message, scene);
        if (cached != null) {
            log.info("知识库命中，跳过 AI API 调用");
            return cached;
        }

        // 2. 知识库未命中，调真实 AI
        String answer = callAI(message, scene);

        // 3. 保存到知识库
        knowledgeBaseService.saveAnswer(message, answer, scene);

        return answer;
    }

    private String callAI(String message, String scene) {
        String prompt = buildPrompt(message, scene);

        // 没配置 API Key 时用 mock
        if (apiKey == null || apiKey.isEmpty()) {
            log.warn("未配置 AI API Key，使用模拟回答");
            return mockResponse(message, scene);
        }

        try {
            return callLLMApi(prompt);
        } catch (Exception e) {
            log.error("AI API 调用失败: {}", e.getMessage());
            return mockResponse(message, scene);
        }
    }

    private String callLLMApi(String prompt) throws Exception {
        String body = String.format("""
            {"model":"%s","messages":[{"role":"system","content":"你是一个生活常识助手。回答要通俗易懂，新手友好，步骤明确。结构化输出JSON。"},{"role":"user","content":"%s"}],"temperature":0.7,"max_tokens":2000}
            """, model, escapeJson(prompt));

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(apiUrl))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + apiKey)
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("API error: " + response.statusCode() + " - " + response.body());
        }

        JsonNode root = objectMapper.readTree(response.body());
        return root.path("choices").path(0).path("message").path("content").asText();
    }

    private String buildPrompt(String message, String scene) {
        String scenePrompt;
        switch (scene != null ? scene : "other") {
            case "cooking":
                scenePrompt = "你是一个新手做饭助手。用通俗易懂的语言回答，假设用户完全没有下厨经验。"
                    + "请用 JSON 格式回答，格式：{\"title\":\"菜名\",\"difficulty\":\"难度\",\"time\":\"时间\",\"servings\":\"份量\","
                    + "\"ingredients\":[\"食材1\",\"食材2\"],\"steps\":[\"步骤1\",\"步骤2\"],\"tips\":\"新手提示\"}";
                break;
            case "shopping":
                scenePrompt = "你是一个买菜/买水果指南专家。回答要实用、可操作，给出具体的挑选方法（看、闻、摸等）。"
                    + "请用 JSON 格式回答，包含品类、挑选步骤（每个步骤含步骤名称和具体操作）、常见误区、总结口诀。";
                break;
            case "repair":
                scenePrompt = "你是一个家庭修理小助手。安全第一，先判断问题严重程度。"
                    + "小问题给步骤，大问题建议找专业人士。每个步骤要写清需要什么工具、操作方向。";
                break;
            case "housework":
                scenePrompt = "你是一个家务技巧专家。回答要简单有效，推荐最常见的方法。"
                    + "告诉用户需要什么材料、每种材料的替代方案。注意安全提示。";
                break;
            case "health":
                scenePrompt = "你是一个健康生活助手。回答要科学、通俗，适合普通读者。"
                    + "涉及疾病症状时，先提示'建议咨询医生'，再给常识性建议。"
                    + "包括：常见小病处理、用药常识、营养建议、急救知识等。";
                break;
            case "fashion":
                scenePrompt = "你是一个穿搭顾问。回答要根据场景给出搭配建议，具体到单品款式、颜色搭配。"
                    + "用户可能是新手，要去繁就简，给出可直接照做的方案。"
                    + "包括：场合穿搭、色彩搭配、衣物护理、身材修饰技巧。";
                break;
            case "etiquette":
                scenePrompt = "你是一个社交礼仪顾问。回答要得体、实用，给出具体的礼节规范和行为建议。"
                    + "包括：餐桌礼仪、职场礼仪、人际交往技巧、送礼指南等。"
                    + "对于文化差异要说明。";
                break;
            case "pet":
                scenePrompt = "你是一个宠物照顾助手。适合养宠新手，回答要详细、安全第一。"
                    + "包括：喂养指南、日常护理、常见疾病识别、行为训练方法。"
                    + "涉及健康问题时，先提示'建议咨询兽医'。";
                break;
            default:
                scenePrompt = "你是一个生活常识助手。用通俗易懂的语言回答日常生活中的问题，新手友好，步骤明确。";
                break;
        }
        return scenePrompt + "\n\n用户问题：" + message;
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private String mockResponse(String message, String scene) {
        if (message.contains("西红柿") || message.contains("鸡蛋")) {
            return "{\"title\":\"🍅 西红柿炒鸡蛋\",\"difficulty\":\"⭐ 入门级\",\"time\":\"10 分钟\",\"servings\":\"1-2 人份\",\"ingredients\":[\"西红柿 × 2 个\",\"鸡蛋 × 3 个\",\"盐 适量\",\"糖 少许\"],\"steps\":[\"1️⃣ 西红柿切块\",\"2️⃣ 鸡蛋打散加盐\",\"3️⃣ 热锅倒油炒蛋，刚凝固就盛出\",\"4️⃣ 炒西红柿到出汁\",\"5️⃣ 加糖盐，倒回鸡蛋翻炒出锅\"],\"tips\":\"鸡蛋不要炒老了，底部刚凝固就盛出来\"}";
        }
        return "收到你的问题：" + message + "\n\n这是一个很好的问题！由于未配置 AI API，这是模拟回复。";
    }
}
