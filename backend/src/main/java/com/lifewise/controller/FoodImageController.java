package com.lifewise.controller;

import com.lifewise.common.ApiResponse;
import com.lifewise.entity.FoodImageCache;
import com.lifewise.repository.FoodImageCacheRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/api/food-image")
public class FoodImageController {

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
    private final FoodImageCacheRepository cacheRepository;

    public FoodImageController(FoodImageCacheRepository cacheRepository) {
        this.cacheRepository = cacheRepository;
    }

    @Value("${gl-image.api-key}")
    private String glApiKey;

    @Value("${gl-image.api-url}")
    private String glApiUrl;

    @PostMapping("/generate")
    public ApiResponse<?> generateFoodImage(@RequestBody Map<String, String> request) {
        String dishName = request.get("dishName");
        if (dishName == null || dishName.trim().isEmpty()) {
            return ApiResponse.error(400, "dishName is required");
        }

        // 1) 查缓存：是否已有这张菜的图片
        Optional<FoodImageCache> cached = cacheRepository.findByDishName(dishName.trim());
        if (cached.isPresent()) {
            FoodImageCache c = cached.get();
            c.setHitCount(c.getHitCount() + 1);
            cacheRepository.save(c);
            log.info("Food image cache HIT: dishName={}, hitCount={}", dishName, c.getHitCount());
            return ApiResponse.success(Map.of(
                "cached", true,
                "imageUrl", c.getImageUrl()
            ));
        }
        log.info("Food image cache MISS: dishName={}, submitting task", dishName);

        // 2) 未命中：提交生图任务
        try {
            String prompt = "A beautiful plate of " + dishName
                    + ", Chinese home cooking style, food photography,"
                    + " warm lighting, high quality, realistic, appetizing,"
                    + " garnished with scallions and sesame, on a ceramic plate,"
                    + " professional food photography, 8K, highly detailed";

            String submitUrl = glApiUrl + "/v1/media/generate";
            String submitBody = objectMapper.writeValueAsString(Map.of(
                    "model", "gpt-image-2",
                    "prompt", prompt
            ));

            HttpRequest submitReq = HttpRequest.newBuilder()
                    .uri(URI.create(submitUrl))
                    .header("Authorization", "Bearer " + glApiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(submitBody))
                    .build();

            HttpResponse<String> submitResp = httpClient.send(submitReq, HttpResponse.BodyHandlers.ofString());
            if (submitResp.statusCode() != 200) {
                log.error("Submit failed: {} {}", submitResp.statusCode(), submitResp.body());
                return ApiResponse.error(502, "Image generation submission failed");
            }

            var submitJson = objectMapper.readTree(submitResp.body());
            long taskId = submitJson.path("data").path("task_id").asLong();
            log.info("Food image task submitted: task_id={}, dishName={}", taskId, dishName);

            return ApiResponse.success(Map.of(
                "cached", false,
                "taskId", taskId,
                "dishName", dishName
            ));

        } catch (Exception e) {
            log.error("Food image submission error", e);
            return ApiResponse.error(500, "Internal error: " + e.getMessage());
        }
    }

    @GetMapping("/status")
    public ApiResponse<?> getStatus(@RequestParam long taskId) {
        try {
            String statusUrl = glApiUrl + "/v1/skills/task-status?task_id=" + taskId;

            HttpRequest statusReq = HttpRequest.newBuilder()
                    .uri(URI.create(statusUrl))
                    .header("Authorization", "Bearer " + glApiKey)
                    .GET()
                    .build();

            HttpResponse<String> statusResp = httpClient.send(statusReq, HttpResponse.BodyHandlers.ofString());
            if (statusResp.statusCode() != 200) {
                return ApiResponse.error(502, "Failed to query status");
            }

            var statusJson = objectMapper.readTree(statusResp.body());
            boolean isFinal = statusJson.path("is_final").asBoolean(false);
            String resultUrl = statusJson.path("result_url").asText("");
            double cost = statusJson.path("cost").asDouble(0);
            String state = statusJson.path("state").asText("");
            String progress = statusJson.path("progress").asText("");

            return ApiResponse.success(Map.of(
                    "isFinal", isFinal,
                    "resultUrl", resultUrl,
                    "cost", cost,
                    "state", state,
                    "progress", progress
            ));

        } catch (Exception e) {
            return ApiResponse.error(500, "Error: " + e.getMessage());
        }
    }

    /** 前端拿到生图结果后回传保存到缓存 */
    @PostMapping("/cache")
    public ApiResponse<?> saveCache(@RequestBody Map<String, String> request) {
        String dishName = request.get("dishName");
        String imageUrl = request.get("imageUrl");
        if (dishName == null || dishName.trim().isEmpty() || imageUrl == null || imageUrl.trim().isEmpty()) {
            return ApiResponse.error(400, "dishName and imageUrl are required");
        }
        try {
            // 去重：已有则更新 URL（可能链接变了）
            Optional<FoodImageCache> existing = cacheRepository.findByDishName(dishName.trim());
            if (existing.isPresent()) {
                FoodImageCache c = existing.get();
                c.setImageUrl(imageUrl);
                cacheRepository.save(c);
                log.info("Food image cache UPDATED: dishName={}", dishName);
            } else {
                FoodImageCache c = new FoodImageCache();
                c.setDishName(dishName.trim());
                c.setImageUrl(imageUrl);
                c.setHitCount(0);
                cacheRepository.save(c);
                log.info("Food image cache SAVED: dishName={}", dishName);
            }
            return ApiResponse.success(Map.of("saved", true));
        } catch (Exception e) {
            log.error("Food image cache save error", e);
            return ApiResponse.error(500, "Save failed: " + e.getMessage());
        }
    }
}
