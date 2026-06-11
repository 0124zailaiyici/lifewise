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
            .connectTimeout(Duration.ofSeconds(15))
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

    @Value("${ai.dashscope-api-key:}")
    private String dashscopeApiKey;

    @Value("${app.upload-dir:./uploads}")
    private String uploadDir;

    // ---------- lookup ----------

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

    // ---------- generate ----------

        @PostMapping("/generate")
    public ApiResponse<?> generateFoodImage(@RequestBody Map<String, String> request) {
        String dishName = request.get("dishName");
        if (dishName == null || dishName.trim().isEmpty()) {
            return ApiResponse.error(400, "dishName is required");
        }
        String scene = request.get("scene");
        boolean isScene = scene != null && ("fashion".equals(scene) || "shopping".equals(scene) || "repair".equals(scene) || "housework".equals(scene));
        if (!isScene) {
            dishName = normalizeDishName(dishName);
        }

        if (!isScene) {
            Optional<FoodImageCache> cached = cacheRepository.findByDishName(dishName);
            if (cached.isPresent()) {
                FoodImageCache c = cached.get();
                c.setHitCount((c.getHitCount() == null ? 0 : c.getHitCount()) + 1);
                cacheRepository.save(c);
                log.info("Food image HIT: dishName={}", dishName);
                return ApiResponse.success(Map.of("cached", true, "found", true, "source", sourceOf(c.getImageUrl()), "imageUrl", c.getImageUrl()));
            }

            Optional<Map<String, Object>> local = findLocalPrebuiltImage(dishName);
            if (local.isPresent()) {
                Map<String, Object> item = local.get();
                log.info("Food image local HIT: dishName={}", dishName);
                return ApiResponse.success(Map.of("cached", true, "found", true, "source", "local-prebuilt", "imageUrl", String.valueOf(item.get("imageUrl"))));
            }
        }

        log.info("Image gen: dishName={}, scene={}", dishName, scene);

        if (glApiKey != null && !glApiKey.isBlank()) {
            return submitToRelay(dishName);
        }
        if (dashscopeApiKey != null && !dashscopeApiKey.isBlank()) {
            return submitToDashScope(dishName);
        }
        return ApiResponse.error(503, "Image generation is not configured");
    }
    // ---------- relay (lk888) ----------

    private ApiResponse<?> submitToRelay(String dishName) {
        try {
            String prompt = buildPrompt(dishName);
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
                log.error("Relay submit failed: {} {}", submitResp.statusCode(), submitResp.body());
                return ApiResponse.error(502, "Image generation submission failed");
            }

            int bizCode = submitJson.path("code").asInt(200);
            if (bizCode != 200) {
                String msg = submitJson.path("msg").asText("Unknown error");
                log.warn("Relay API code={}: {}", bizCode, msg);
                return ApiResponse.error(502, msg);
            }

            long taskId = submitJson.path("data").path("task_id").asLong(0);
            if (taskId == 0) {
                log.error("Relay returned 0 task_id: {}", submitResp.body());
                return ApiResponse.error(502, "Failed to get task ID");
            }

            log.info("Relay task submitted: task_id={}, dishName={}", taskId, dishName);
            return ApiResponse.success(Map.of(
                    "cached", false, "found", false,
                    "taskId", String.valueOf(taskId),
                    "provider", "relay",
                    "dishName", dishName
            ));
        } catch (Exception e) {
            log.error("Relay submission error", e);
            return ApiResponse.error(500, "Internal error: " + e.getMessage());
        }
    }

    // ---------- DashScope (千问/通义万相) ----------

    private ApiResponse<?> submitToDashScope(String dishName) {
        try {
            String prompt = buildPrompt(dishName);
            String submitUrl = "https://dashscope.aliyuncs.com/api/v1/services/aigc/text2image/image-synthesis";
            String submitBody = objectMapper.writeValueAsString(Map.of(
                    "model", "wanx-v1",
                    "input", Map.of("prompt", prompt),
                    "parameters", Map.of("size", "1024*1024", "n", 1, "negative_prompt", "???????????????????")
            ));

            HttpRequest submitReq = HttpRequest.newBuilder()
                    .uri(URI.create(submitUrl))
                    .header("Authorization", "Bearer " + dashscopeApiKey)
                    .header("Content-Type", "application/json")
                    .header("X-DashScope-Async", "enable")
                    .POST(HttpRequest.BodyPublishers.ofString(submitBody))
                    .build();

            HttpResponse<String> submitResp = httpClient.send(submitReq, HttpResponse.BodyHandlers.ofString());
            var submitJson = objectMapper.readTree(submitResp.body());

            if (submitResp.statusCode() != 200) {
                log.error("DashScope submit failed: {} {}", submitResp.statusCode(), submitResp.body());
                return ApiResponse.error(502, "DashScope image generation failed");
            }

            String taskId = submitJson.path("output").path("task_id").asText("");
            if (taskId.isEmpty()) {
                log.error("DashScope returned no task_id: {}", submitResp.body());
                return ApiResponse.error(502, "Failed to get task ID from DashScope");
            }

            // Check if already completed
            String status = submitJson.path("output").path("task_status").asText("");
            if ("SUCCEEDED".equals(status)) {
                var results = submitJson.path("output").path("results");
                if (results.isArray() && results.size() > 0) {
                    String imageUrl = results.get(0).path("url").asText("");
                    if (!imageUrl.isEmpty()) {
                        log.info("DashScope image completed immediately: dishName={}", dishName);
                        cacheImage(dishName, imageUrl);
                        return ApiResponse.success(Map.of(
                                "cached", false, "found", true,
                                "source", "dashscope",
                                "imageUrl", imageUrl
                        ));
                    }
                }
            }

            log.info("DashScope task submitted: task_id={}, dishName={}", taskId, dishName);
            return ApiResponse.success(Map.of(
                    "cached", false, "found", false,
                    "taskId", taskId,
                    "provider", "dashscope",
                    "dishName", dishName
            ));
        } catch (Exception e) {
            log.error("DashScope submission error", e);
            return ApiResponse.error(500, "DashScope error: " + e.getMessage());
        }
    }

    // ---------- status ----------

    @GetMapping("/status")
    public ApiResponse<?> getStatus(@RequestParam String taskId, @RequestParam(defaultValue = "relay") String provider) {
        if ("dashscope".equals(provider)) {
            return getDashScopeStatus(taskId);
        }
        return getRelayStatus(taskId);
    }

    private ApiResponse<?> getRelayStatus(String taskIdStr) {
        try {
            long taskId = Long.parseLong(taskIdStr);
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

    private ApiResponse<?> getDashScopeStatus(String taskId) {
        try {
            String statusUrl = "https://dashscope.aliyuncs.com/api/v1/tasks/" + taskId;
            HttpRequest statusReq = HttpRequest.newBuilder()
                    .uri(URI.create(statusUrl))
                    .header("Authorization", "Bearer " + dashscopeApiKey)
                    .GET()
                    .build();

            HttpResponse<String> statusResp = httpClient.send(statusReq, HttpResponse.BodyHandlers.ofString());
            if (statusResp.statusCode() != 200) {
                return ApiResponse.error(502, "Failed to query DashScope status");
            }

            var statusJson = objectMapper.readTree(statusResp.body());
            String taskStatus = statusJson.path("output").path("task_status").asText("");
            boolean isFinal = "SUCCEEDED".equals(taskStatus) || "FAILED".equals(taskStatus);
            String resultUrl = "";
            if ("SUCCEEDED".equals(taskStatus)) {
                var results = statusJson.path("output").path("results");
                if (results.isArray() && results.size() > 0) {
                    resultUrl = results.get(0).path("url").asText("");
                }
            }

            return ApiResponse.success(Map.of(
                    "isFinal", isFinal,
                    "resultUrl", resultUrl,
                    "cost", 0,
                    "state", taskStatus,
                    "progress", "SUCCEEDED".equals(taskStatus) ? "100%" : taskStatus
            ));
        } catch (Exception e) {
            return ApiResponse.error(500, "DashScope status error: " + e.getMessage());
        }
    }

    // ---------- cache ----------

    @PostMapping("/cache")
    public ApiResponse<?> saveCache(@RequestBody Map<String, String> request) {
        String dishName = request.get("dishName");
        String imageUrl = request.get("imageUrl");
        if (dishName == null || dishName.trim().isEmpty() || imageUrl == null || imageUrl.trim().isEmpty()) {
            return ApiResponse.error(400, "dishName and imageUrl are required");
        }
        dishName = normalizeDishName(dishName);
        cacheImage(dishName, imageUrl);
        return ApiResponse.success(Map.of("saved", true));
    }

    private void cacheImage(String dishName, String imageUrl) {
        try {
            dishName = normalizeDishName(dishName);
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
        } catch (Exception e) {
            log.warn("Failed to cache image: {}", e.getMessage());
        }
    }

    // ---------- helpers ----------

        private String buildPrompt(String dishName) {
        // For scene images (fashion/shopping), dishName already contains the English prompt
        return dishName;
    }

    private String normalizeDishName(String dishName) {
        if (dishName == null) return "";
        dishName = repairMojibake(dishName);
        String v = dishName.trim()
                .replaceAll("[\\s\u3000]+", "")
                .replaceAll("[\uFF0C\u3002\uFF01\uFF1F\u3001\uFF1A:\uFF0C,.!?]", "");
        v = v.replaceFirst("^(\u5BB6\u5E38|\u7ECF\u5178|\u6B63\u5B97|\u7B80\u5355|\u61D2\u4EBA|\u5FEB\u624B|\u4E0B\u996D|\u597D\u5403\u7684)", "");
        v = v.replaceFirst("(\u7684\u505A\u6CD5|\u505A\u6CD5|\u6559\u7A0B|\u83DC\u8C31|\u600E\u4E48\u505A|\u914D\u65B9)$", "");
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
        } catch (Exception ignored) {}
        try {
            String mojibake = new String(candidate.getBytes(StandardCharsets.UTF_8), Charset.forName("windows-1252"));
            return target.equals(normalizeDishName(mojibake));
        } catch (Exception ignored) {
            return false;
        }
    }
}
