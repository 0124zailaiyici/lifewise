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
import java.util.List;

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

    @Override
    public String chat(String message, String scene, Long userId, Long conversationId) {
        // 1. 先查知识库缓存
        String cached = knowledgeBaseService.findAnswer(message, scene);
        if (cached != null) {
            log.info("知识库命中，跳过 AI API 调用");
            return cached;
        }

        // 2. 知识库未命中，调真实 AI
        String answer = callAI(message, scene, conversationId);

        // 3. 保存到知识库
        knowledgeBaseService.saveAnswer(message, answer, scene);

        return answer;
    }

    private String callAI(String message, String scene, Long conversationId) {
        if (apiKey == null || apiKey.isEmpty()) {
            log.warn("未配置 AI API Key，使用模拟回答");
            return mockResponse(message, scene);
        }

        try {
            return callLLMApi(message, scene, conversationId);
        } catch (Exception e) {
            log.error("AI API 调用失败: {}", e.getMessage());
            return mockResponse(message, scene);
        }
    }

    private String callLLMApi(String message, String scene, Long conversationId) throws Exception {
        // 构建消息列表
        List<String> messageJsons = new ArrayList<>();

        // 1. system prompt
        String systemPrompt = buildSystemPrompt(scene);
        messageJsons.add("{\"role\":\"system\",\"content\":" + toJsonString(systemPrompt) + "}");

        // 2. 对话历史（如果有 conversationId）
        if (conversationId != null) {
            List<Message> history = messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId);
            for (Message msg : history) {
                String content = msg.getContent();
                if (msg.getImageUrl() != null && !msg.getImageUrl().isEmpty()) {
                    content += " [图片: " + msg.getImageUrl() + "]";
                }
                messageJsons.add("{\"role\":\"" + msg.getRole() + "\",\"content\":" + toJsonString(content) + "}");
            }
        }

        // 3. 当前用户消息
        messageJsons.add("{\"role\":\"user\",\"content\":" + toJsonString(message) + "}");

        // 组装请求体
        String messagesArray = "[" + String.join(",", messageJsons) + "]";
        String body = String.format("""
            {"model":"%s","messages":%s,"temperature":0.7,"max_tokens":2000}
            """, model, messagesArray);

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

    private String toJsonString(String s) {
        return "\"" + s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t") + "\"";
    }

    private String buildSystemPrompt(String scene) {
        String baseRule = """
你是 LifeWise 生活常识助手，专门帮助缺乏生活经验的新手解决日常问题。
## 核心要求
1. 回答要通俗易懂，假设用户完全没有相关经验
2. 步骤要清晰、具体、可操作
3. 安全第一，涉及危险操作（用电、用火、化学品等）必须提醒
4. 输出格式必须是纯 JSON，不要包含 markdown 代码块标记
5. 不要输出任何解释性文字，只输出 JSON
""";

        String schema;
        switch (scene != null ? scene : "other") {
            case "cooking":
                schema = """
## 场景：做饭助手
输出 JSON 格式：
{
  "title": "菜名",
  "difficulty": "入门/初级/中级",
  "time": "预计时间（分钟）",
  "servings": "份量",
  "ingredients": [
    {"name": "食材名", "amount": "用量", "note": "替代方案或注意事项（可选）"}
  ],
  "steps": [
    {"step": 1, "action": "操作描述", "tip": "新手提示（可选）"}
  ],
  "tips": "整体注意事项",
  "key_point": "这道菜最关键的一步是什么"
}
""";
                break;
            case "shopping":
                schema = """
## 场景：买菜/水果挑选指南
输出 JSON 格式：
{
  "category": "品类名称",
  "season": "当前是否应季",
  "selection_steps": [
    {"step_name": "步骤名（如：看外观）", "action": "具体操作描述"}
  ],
  "common_mistakes": ["常见误区1", "常见误区2"],
  "storage_tip": "保存方法",
  "summary_slogan": "总结口诀"
}
""";
                break;
            case "repair":
                schema = """
## 场景：家庭修理指南
输出 JSON 格式：
{
  "problem": "问题描述",
  "severity": "轻微/中等/严重",
  "need_professional": false,
  "tools": ["所需工具1", "所需工具2"],
  "steps": [
    {"step": 1, "action": "操作描述", "warning": "安全提醒（可选）"}
  ],
  "professional_advice": "如果问题复杂，建议找专业人士的情况说明"
}
""";
                break;
            case "housework":
                schema = """
## 场景：家务技巧
输出 JSON 格式：
{
  "problem": "问题描述",
  "difficulty": "简单/中等/困难",
  "materials": [
    {"name": "材料名", "alternative": "替代品（可选）"}
  ],
  "steps": [
    {"step": 1, "action": "操作描述"}
  ],
  "safety_tip": "安全提示",
  "prevention": "如何预防此类问题"
}
""";
                break;
            case "health":
                schema = """
## 场景：健康常识
输出 JSON 格式：
{
  "question": "问题描述",
  "category": "症状处理/用药常识/营养建议/急救知识",
  "disclaimer": "⚠️ 本回答仅供参考，不能替代专业医疗建议",
  "suggestions": [
    {"item": "建议项", "detail": "详细说明"}
  ],
  "when_to_see_doctor": "什么情况下必须去看医生",
  "prevention": "日常预防措施"
}
""";
                break;
            case "fashion":
                schema = """
## 场景：穿搭指南
输出 JSON 格式：
{
  "occasion": "场合",
  "style": "推荐风格",
  "color_palette": ["推荐颜色1", "推荐颜色2"],
  "outfits": [
    {"piece": "单品", "description": "款式建议", "color": "推荐颜色"}
  ],
  "avoid": "避免什么",
  "tips": "穿搭小技巧"
}
""";
                break;
            case "etiquette":
                schema = """
## 场景：社交礼仪
输出 JSON 格式：
{
  "occasion": "场合",
  "key_principles": ["原则1", "原则2"],
  "do_list": [
    {"action": "应该做的事", "reason": "为什么"}
  ],
  "dont_list": [
    {"action": "不应该做的事", "reason": "为什么"}
  ],
  "cultural_notes": "文化差异说明（如适用）"
}
""";
                break;
            case "pet":
                schema = """
## 场景：宠物照顾
输出 JSON 格式：
{
  "pet_type": "宠物类型",
  "topic": "问题主题",
  "difficulty": "入门/初级/中级",
  "steps": [
    {"step": 1, "action": "操作描述", "warning": "安全提醒（可选）"}
  ],
  "common_mistakes": ["常见错误1", "常见错误2"],
  "when_to_see_vet": "什么情况下需要看兽医"
}
""";
                break;
            default:
                schema = """
## 通用模式
输出 JSON 格式：
{
  "question": "用户问题",
  "answer": "详细回答",
  "tips": ["小贴士1", "小贴士2"]
}
""";
                break;
        }
        return baseRule + "\n" + schema;
    }

    private String mockResponse(String message, String scene) {
        if (message.contains("辣椒炒肉") || message.contains("西红柿") || message.contains("鸡蛋")) {
            return "{\"title\":\"辣椒炒肉\",\"difficulty\":\"入门\",\"time\":\"20分钟\",\"servings\":\"2人份\",\"ingredients\":[{\"name\":\"五花肉\",\"amount\":\"200克\",\"note\":\"带点肥肉更香\"},{\"name\":\"青辣椒\",\"amount\":\"4-5个\",\"note\":\"喜欢吃辣可以用螺丝椒\"},{\"name\":\"大蒜\",\"amount\":\"3瓣\",\"note\":\"\"},{\"name\":\"生抽\",\"amount\":\"2汤匙\",\"note\":\"\"},{\"name\":\"盐\",\"amount\":\"少许\",\"note\":\"\"}],\"steps\":[{\"step\":1,\"action\":\"五花肉切薄片，用1汤匙生抽腌5分钟\",\"tip\":\"肉放冰箱冻半小时更好切\"},{\"step\":2,\"action\":\"青辣椒切丝或小段，大蒜拍碎切末\"},{\"step\":3,\"action\":\"热锅倒油，放入肉片快速翻炒至变色卷曲，盛出备用\"},{\"step\":4,\"action\":\"锅里留底油，爆香蒜末，倒入青辣椒大火快炒至表面微焦\"},{\"step\":5,\"action\":\"倒回肉片，加剩下的生抽和少许盐，翻炒半分钟出锅\"}],\"tips\":\"五花肉带点肥肉炒出来更香；辣椒一定要大火快炒才脆嫩；盐最后放先尝味道\",\"key_point\":\"辣椒要大火快炒才脆嫩\"}";
        }
        if (message.contains("西瓜") || message.contains("水果")) {
            return "{\"category\":\"西瓜\",\"season\":\"夏季应季\",\"selection_steps\":[{\"step_name\":\"看外观\",\"action\":\"选深绿带光泽、纹路清晰均匀的；瓜底部凹陷深、圆圈小的皮薄肉甜；瓜藤绿色弯曲粗壮更新鲜\"},{\"step_name\":\"摸表面\",\"action\":\"摸瓜皮光滑不扎手；轻按有弹性说明熟度适中\"},{\"step_name\":\"听声音\",\"action\":\"指关节轻敲，声音低沉浑厚像敲鼓为好；声音清脆像敲木头则生\"},{\"step_name\":\"掂重量\",\"action\":\"同样大小选偏轻的，水分少糖分高更甜\"}],\"common_mistakes\":[\"不要只看瓜蒂是否弯曲，关键是颜色和鲜活度\",\"拍打时用指关节不是指尖\"],\"storage_tip\":\"常温阴凉处保存，切开后冷藏\",\"summary_slogan\":\"一看二摸三听四掂，凹陷深、藤新鲜、声音砰就是好瓜\"}";
        }
        return "{\"question\":\"" + message + "\",\"answer\":\"这是一个很好的问题！由于当前处于开发模式（未配置完整 AI API），这是模拟回复。配置好 API Key 后，我会给出专业、结构化的回答。\",\"tips\":[\"你可以先在设置中配置 API Key\",\"配置后刷新页面重试\"]}";
    }
}