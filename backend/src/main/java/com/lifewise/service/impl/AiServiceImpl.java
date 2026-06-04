package com.lifewise.service.impl;

import com.lifewise.service.AiService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
public class AiServiceImpl implements AiService {

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${ai.api-url:}")
    private String apiUrl;

    @Value("${ai.api-key:}")
    private String apiKey;

    @Value("${ai.model:deepseek-chat}")
    private String model;

    @Override
    public String chat(String message, String scene, Long userId) {
        String prompt = buildPrompt(message, scene);

        // 如果配置了 API Key，调用真实 API
        if (apiKey != null && !apiKey.isEmpty()) {
            try {
                return callRealLLM(prompt);
            } catch (Exception e) {
                log.error("LLM API 调用失败，降级使用 mock: {}", e.getMessage());
                return mockResponse(message, scene);
            }
        }

        // 开发阶段：mock 响应
        return mockResponse(message, scene);
    }

    private String callRealLLM(String prompt) throws Exception {
        String body = String.format("""
            {
              "model": "%s",
              "messages": [
                {"role": "system", "content": "你是一个生活常识助手。回答要通俗易懂，新手友好，步骤明确。结构化输出 JSON。"},
                {"role": "user", "content": "%s"}
              ],
              "temperature": 0.7,
              "max_tokens": 1500
            }
            """, model, escapeJson(prompt));

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(apiUrl))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + apiKey)
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("API 返回错误: " + response.statusCode() + " - " + response.body());
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
                    + "按品类给出判断标准，要说具体怎么看、怎么摸。用通俗语言，步骤明确。";
                break;
            case "repair":
                scenePrompt = "你是一个家庭修理小助手。安全第一，先判断问题严重程度。"
                    + "小问题给步骤，大问题建议找专业人士。每个步骤要写清需要什么工具、操作方向。";
                break;
            case "housework":
                scenePrompt = "你是一个家务技巧专家。回答要简单有效，推荐最常见的方法。"
                    + "告诉用户需要什么材料、每种材料的替代方案。注意安全提示。";
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
        String msg = message.toLowerCase();
        if (msg.contains("西红柿") || msg.contains("鸡蛋")) {
            return """
                {
                  "title": "🍅 西红柿炒鸡蛋",
                  "difficulty": "⭐ 入门级",
                  "time": "10 分钟",
                  "servings": "1-2 人份",
                  "ingredients": [
                    "西红柿 × 2 个（选熟透的，更好出汁）",
                    "鸡蛋 × 3 个",
                    "盐 适量",
                    "糖 少许（提鲜的关键）"
                  ],
                  "steps": [
                    "1️⃣ 西红柿切块，大拇指一半大小，不用太小",
                    "2️⃣ 鸡蛋打散，加一小撮盐搅匀",
                    "3️⃣ 热锅倒油，油多一点蛋才蓬松，油热倒蛋液，快速划散，刚凝固就盛出来",
                    "4️⃣ 不用洗锅，中火炒西红柿到出汁变软",
                    "5️⃣ 加半勺糖提鲜、适量盐，倒回鸡蛋翻炒两下出锅"
                  ],
                  "tips": "鸡蛋不要炒老了，底部刚凝固还有点流动就盛出来，余温让它变熟，这样才嫩"
                }""";
        }
        if (msg.contains("西瓜") || msg.contains("挑")) {
            return "三步挑好瓜：\n1. 👀 看 — 瓜皮纹路清晰、底部圆圈小的甜\n2. 👋 拍 — 声音清脆像拍胸膛是好瓜，闷响的别买\n3. ✋ 掂 — 同样大小，越轻的越熟，越甜";
        }
        if (msg.contains("水龙头") || msg.contains("滴水") || msg.contains("修")) {
            return "🔧 水龙头滴水修理\n\n大概率是垫片老化，很好换：\n1. 关掉水龙头下方的角阀（顺时针拧到底）\n2. 用扳手拧下水龙头把手\n3. 取出旧垫片，换上新的（五金店2块钱）\n4. 按顺序装回去\n5. 打开角阀，试试还漏不漏\n\n⚠️ 如果换了垫片还漏，可能是阀芯问题，建议找水电师傅";
        }
        if (msg.contains("买菜") || msg.contains("新鲜")) {
            return "🛒 买菜实用技巧：\n\n🥬 叶菜类：看叶子颜色鲜亮、不发黄，根部不发黑\n🥩 肉类：颜色鲜红有弹性，按下去能回弹\n🐟 鱼类：眼睛清亮不浑浊，鳞片完整有光泽\n🍅 西红柿：选粉红均匀的，不要有青斑\n🥕 胡萝卜：选橙红色、表皮光滑的\n\n💡 小窍门：早市的菜最新鲜，去晚了都是挑剩下的";
        }
        if (msg.contains("洗") || msg.contains("打扫") || msg.contains("家务") || msg.contains("染色")) {
            return "🧹 常见家务问题解答：\n\n白色衣服染色处理：\n1. 准备温水（40度，手摸温热）\n2. 加两勺小苏打 + 一点洗洁精\n3. 泡30分钟后搓洗\n4. 如果还不行，试试84消毒液稀释后浸泡（注意通风）\n\n💡 小窍门：白醋 + 小苏打是万能清洁组合，去油污、去水垢都好用";
        }
        // 通用回复
        return "收到你的问题：" + message + "\n\n这是一个很好的问题！由于目前处于开发模式（未配置 AI API），这是模拟回复。\n\n配置好 DeepSeek 或 OpenAI API Key 后，我会给出专业、结构化的回答。";
    }
}
