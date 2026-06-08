package com.lifewise.controller;

import com.lifewise.common.ApiResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/ai-config")
public class AiConfigController {

    @Value("${ai.api-url:}")
    private String deepseekApiUrl;

    @Value("${ai.api-key:}")
    private String deepseekApiKey;

    @Value("${ai.model:deepseek-v4-flash}")
    private String deepseekModel;

    @Value("${ai.dashscope-api-url:https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions}")
    private String dashscopeApiUrl;

    @Value("${ai.dashscope-api-key:}")
    private String dashscopeApiKey;

    @Value("${ai.dashscope-model:qwen-plus}")
    private String dashscopeModel;

    @Value("${ai.ollama-url:http://localhost:11434/api/chat}")
    private String ollamaUrl;

    @Value("${ai.ollama-model:qwen2.5:7b}")
    private String ollamaModel;

    @Value("${app.vision-enabled:false}")
    private boolean visionEnabled;

    @Value("${ai.vision-api-url:}")
    private String visionApiUrl;

    @Value("${ai.vision-api-key:}")
    private String visionApiKey;

    @Value("${ai.vision-model:mimo-v2-omni}")
    private String visionModel;

    @Value("${gl-image.api-key:}")
    private String imageApiKey;

    @Value("${gl-image.api-url:}")
    private String imageApiUrl;

    @GetMapping("/status")
    public ApiResponse<Map<String, Object>> status() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("defaultProvider", "qwen");
        data.put("autoFallbackToDeepSeek", false);
        data.put("costGuard", "不会从 Qwen/Ollama 自动回退到 DeepSeek；只有手动选择 DeepSeek 且配置 Key 时才会调用。");

        data.put("qwen", provider("千问 Qwen", true, hasText(dashscopeApiKey), dashscopeModel, dashscopeApiUrl));
        data.put("deepseek", provider("DeepSeek", false, hasText(deepseekApiKey), deepseekModel, deepseekApiUrl));
        data.put("ollama", provider("Ollama 本地", false, true, ollamaModel, ollamaUrl));
        data.put("vision", provider("图片识别", visionEnabled, hasText(visionApiKey), visionModel, visionApiUrl));
        data.put("foodImage", provider("菜品生图", false, hasText(imageApiKey), "gpt-image-2", imageApiUrl));
        data.put("warnings", warnings());
        return ApiResponse.success(data);
    }

    private Map<String, Object> provider(String name, boolean recommended, boolean configured, String model, String url) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("name", name);
        item.put("recommended", recommended);
        item.put("configured", configured);
        item.put("model", safe(model));
        item.put("url", safe(url));
        return item;
    }

    private String[] warnings() {
        if (hasText(deepseekApiKey)) {
            return new String[]{
                "检测到 DeepSeek Key 已配置：只有当前端手动选择 DeepSeek 时才会调用，但仍建议不用时清空。"
            };
        }
        if (!hasText(dashscopeApiKey)) {
            return new String[]{
                "千问 Qwen 未配置 Key：默认模型不可用，请在服务器 application-cloud.properties 配置 ai.dashscope-api-key。"
            };
        }
        return new String[0];
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
