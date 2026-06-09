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
import java.nio.charset.StandardCharsets;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
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

    @Value("${gl-image.api-key:}")
    private String glApiKey;

    @Value("${gl-image.api-url:https://api.lk888.ai/api}")
    private String glApiUrl;

    @Value("${app.upload-dir:./uploads}")
    private String uploadDir;

    /**
     * Safe lookup: only checks browser/server/local prebuilt cache, never submits an external generation task.
     */
    @GetMapping("/lookup")
    public ApiResponse<?> lookupFoodImage(@RequestParam String dishName) {
        return lookupByDishName(dishName);
    }

    @PostMapping("/lookup")
    public ApiResponse<?> lookupFoodImagePost(@RequestBody Map<String, String> request) {
        return lookupByDishName(request.get("dishName"));
    }

    private ApiResponse<?> lookupByDishName(String dishName) {
        if (dishName == null || dishName.trim().isEmpty()) {
            return ApiResponse.error(400, "dishName is required");
        }
        String normalized = normalizeDishName(dishName);

        Optional<FoodImageCache> cached = cacheRepository.findByDishName(normalized);
        if (cached.isPresent()) {
            FoodImageCache c = cached.get();
            c.setHitCount((c.getHitCount() == null ? 0 : c.getHitCount()) + 1);
            cacheRepository.save(c);
            return ApiResponse.success(Map.of(
                    "found", true,
                    "cached", true,
                    "source", sourceOf(c.getImageUrl()),
                    "dishName", c.getDishName(),
                    "imageUrl", c.getImageUrl()
            ));
        }

        Optional<Map<String, Object>> local = findLocalPrebuiltImage(normalized);
        if (local.isPresent()) {
            Map<String, Object> item = local.get();
            return ApiResponse.success(Map.of(
                    "found", true,
                    "cached", true,
                    "source", "local-prebuilt",
                    "dishName", String.valueOf(item.get("dishName")),
                    "dishKey", String.valueOf(item.getOrDefault("dishKey", "")),
                    "imageUrl", String.valueOf(item.get("imageUrl"))
            ));
        }

        return ApiResponse.success(Map.of(
                "found", false,
                "cached", false,
                "source", "none",
                "dishName", normalized
        ));
    }

    /**
     * Compatible generate endpoint. It still checks DB/local prebuilt first; only then submits external generation.
     */
    @PostMapping("/generate")
    public ApiResponse<?> generateFoodImage(@RequestBody Map<String, String> request) {
        String dishName = request.get("dishName");
        if (dishName == null || dishName.trim().isEmpty()) {
            return ApiResponse.error(400, "dishName is required");
        }
        dishName = normalizeDishName(dishName);

        Optional<FoodImageCache> cached = cacheRepository.findByDishName(dishName);
        if (cached.isPresent()) {
            FoodImageCache c = cached.get();
            c.setHitCount((c.getHitCount() == null ? 0 : c.getHitCount()) + 1);
            cacheRepository.save(c);
            log.info("Food image cache HIT: dishName={}, hitCount={}", dishName, c.getHitCount());
            return ApiResponse.success(Map.of(
                    "cached", true,
                    "found", true,
                    "source", sourceOf(c.getImageUrl()),
                    "imageUrl", c.getImageUrl()
            ));
        }

        Optional<Map<String, Object>> local = findLocalPrebuiltImage(dishName);
        if (local.isPresent()) {
            Map<String, Object> item = local.get();
            String imageUrl = String.valueOf(item.get("imageUrl"));
            log.info("Food image local prebuilt HIT: dishName={}, imageUrl={}", dishName, imageUrl);
            return ApiResponse.success(Map.of(
                    "cached", true,
                    "found", true,
                    "source", "local-prebuilt",
                    "dishName", String.valueOf(item.get("dishName")),
                    "dishKey", String.valueOf(item.getOrDefault("dishKey", "")),
                    "imageUrl", imageUrl
            ));
        }

        log.info("Food image cache MISS: dishName={}, submitting task", dishName);
        try {
            if (glApiKey == null || glApiKey.isBlank()) {
                return ApiResponse.error(503, "Image generation is not configured");
            }
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
            var submitJson = objectMapper.readTree(submitResp.body());

            if (submitResp.statusCode() != 200) {
                log.error("Submit failed: {} {}", submitResp.statusCode(), submitResp.body());
                return ApiResponse.error(502, "Image generation submission failed");
            }

            int bizCode = submitJson.path("code").asInt(200);
            if (bizCode != 200) {
                String msg = submitJson.path("msg").asText("Unknown error");
                log.warn("Relay API returned code={}: {}", bizCode, msg);
                if (bizCode == 402) {
                    return ApiResponse.error(429, "算力不足，请补充后再试");
                }
                return ApiResponse.error(502, msg);
            }

            long taskId = submitJson.path("data").path("task_id").asLong(0);
            if (taskId == 0) {
                log.error("Relay API returned 0 task_id: {}", submitResp.body());
                return ApiResponse.error(502, "Failed to get task ID from relay");
            }

            log.info("Food image task submitted: task_id={}, dishName={}", taskId, dishName);
            return ApiResponse.success(Map.of(
                    "cached", false,
                    "found", false,
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

    @PostMapping("/cache")
    public ApiResponse<?> saveCache(@RequestBody Map<String, String> request) {
        String dishName = request.get("dishName");
        String imageUrl = request.get("imageUrl");
        if (dishName == null || dishName.trim().isEmpty() || imageUrl == null || imageUrl.trim().isEmpty()) {
            return ApiResponse.error(400, "dishName and imageUrl are required");
        }
        dishName = normalizeDishName(dishName);
        try {
            Optional<FoodImageCache> existing = cacheRepository.findByDishName(dishName);
            if (existing.isPresent()) {
                FoodImageCache c = existing.get();
                c.setImageUrl(imageUrl);
                cacheRepository.save(c);
                log.info("Food image cache UPDATED: dishName={}", dishName);
            } else {
                FoodImageCache c = new FoodImageCache();
                c.setDishName(dishName);
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

    private String normalizeDishName(String dishName) {
        if (dishName == null) return "";
        dishName = repairMojibake(dishName);
        String v = dishName.trim()
                .replaceAll("[\\s　]+", "")
                .replaceAll("[，。！？、：:；;,.!?]", "");
        v = v.replaceFirst("^(家常|经典|正宗|简单|懒人|快手|下饭|好吃的)", "");
        v = v.replaceFirst("(的做法|做法|教程|菜谱|怎么做|配方)$", "");
        return v.trim();
    }

    private String repairMojibake(String value) {
        if (value == null || value.isBlank()) return value;
        boolean hasCjk = value.codePoints().anyMatch(cp -> cp >= 0x4E00 && cp <= 0x9FFF);
        if (hasCjk) return value;
        String repaired = tryDecodeSingleByteMojibake(value, StandardCharsets.ISO_8859_1);
        if (hasCjk(repaired)) return repaired;
        repaired = tryDecodeSingleByteMojibake(value, Charset.forName("windows-1252"));
        return hasCjk(repaired) ? repaired : value;
    }

    private String tryDecodeSingleByteMojibake(String value, Charset singleByteCharset) {
        try {
            return new String(value.getBytes(singleByteCharset), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            return value;
        }
    }

    private boolean hasCjk(String value) {
        return value != null && value.codePoints().anyMatch(cp -> cp >= 0x4E00 && cp <= 0x9FFF);
    }

    private String sourceOf(String imageUrl) {
        if (imageUrl != null && imageUrl.startsWith("/uploads/food-images/")) return "local-prebuilt";
        return "remote-cache";
    }

    @SuppressWarnings("unchecked")
    private Optional<Map<String, Object>> findLocalPrebuiltImage(String dishName) {
        try {
            Path index = Path.of(uploadDir, "food-images", "v1", "index.json");
            if (!Files.exists(index)) return Optional.empty();
            var root = objectMapper.readTree(index.toFile());
            if (!root.has("items") || !root.path("items").isArray()) return Optional.empty();
            String target = normalizeDishName(dishName);
            for (var item : root.path("items")) {
                String name = normalizeDishName(item.path("dishName").asText(""));
                if (matchesDishName(target, name)) return Optional.of(objectMapper.convertValue(item, Map.class));
                if (item.has("aliases") && item.path("aliases").isArray()) {
                    for (var alias : item.path("aliases")) {
                        if (matchesDishName(target, normalizeDishName(alias.asText("")))) {
                            return Optional.of(objectMapper.convertValue(item, Map.class));
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Food image local manifest lookup failed: {}", e.getMessage());
        }
        return Optional.empty();
    }

    private boolean matchesDishName(String target, String candidate) {
        if (target == null || candidate == null) return false;
        if (target.equals(candidate)) return true;
        try {
            String mojibake = new String(candidate.getBytes(StandardCharsets.UTF_8), StandardCharsets.ISO_8859_1);
            if (target.equals(normalizeDishName(mojibake))) return true;
        } catch (Exception ignored) {
        }
        try {
            String mojibake = new String(candidate.getBytes(StandardCharsets.UTF_8), Charset.forName("windows-1252"));
            return target.equals(normalizeDishName(mojibake));
        } catch (Exception ignored) {
            return false;
        }
    }
}
