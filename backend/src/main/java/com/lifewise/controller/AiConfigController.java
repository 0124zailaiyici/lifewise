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

    @Value("${ai.deepseek-enabled:false}")
    private boolean deepseekEnabled;

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
        data.put("costGuard", "\u4e0d\u4f1a\u4ece Qwen/Ollama \u81ea\u52a8\u56de\u9000\u5230 DeepSeek\uff1bDeepSeek \u9ed8\u8ba4\u670d\u52a1\u7aef\u7981\u7528\uff0c\u5fc5\u987b\u663e\u5f0f\u5f00\u542f ai.deepseek-enabled=true \u624d\u4f1a\u8c03\u7528\u3002");
        data.put("deepseekEnabled", deepseekEnabled);

        data.put("qwen", provider("千问 Qwen", true, hasText(dashscopeApiKey), dashscopeModel, dashscopeApiUrl));
        data.put("deepseek", provider("DeepSeek", false, deepseekEnabled && hasText(deepseekApiKey), deepseekModel, deepseekApiUrl));
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
        if (deepseekEnabled && hasText(deepseekApiKey)) {
            return new String[]{
                "\u68c0\u6d4b\u5230 DeepSeek \u5df2\u542f\u7528\u4e14 Key \u5df2\u914d\u7f6e\uff1a\u53ea\u6709\u5f53\u524d\u7aef\u624b\u52a8\u9009\u62e9 DeepSeek \u65f6\u624d\u4f1a\u8c03\u7528\uff0c\u4ecd\u5efa\u8bae\u4e0d\u7528\u65f6\u5173\u95ed ai.deepseek-enabled\u3002"
            };
        }
        if (hasText(deepseekApiKey)) {
            return new String[]{
                "\u68c0\u6d4b\u5230 DeepSeek Key \u5b58\u5728\uff0c\u4f46\u670d\u52a1\u7aef\u5f00\u5173\u672a\u542f\u7528\uff1a\u5f53\u524d\u4e0d\u4f1a\u8c03\u7528 DeepSeek\u3002"
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
