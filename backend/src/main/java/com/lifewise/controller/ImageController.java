package com.lifewise.controller;

import com.lifewise.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/images")
@RequiredArgsConstructor
public class ImageController {

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();

    @Value("${app.image-api-key:}")
    private String imageApiKey;

    @GetMapping("/search")
    public ApiResponse<?> searchImages(@RequestParam String q) {
        if (q == null || q.trim().isEmpty()) {
            return ApiResponse.success(Map.of("keyword", "", "urls", List.of()));
        }
        String keyword = q.trim();
        if (imageApiKey != null && !imageApiKey.isEmpty()) {
            try {
                List<String> urls = searchPixabay(keyword);
                if (!urls.isEmpty()) {
                    return ApiResponse.success(Map.of("keyword", keyword, "urls", urls));
                }
            } catch (Exception e) {
                log.warn("Pixabay search failed: {}", e.getMessage());
            }
        }
        String hashStr = Integer.toHexString(keyword.hashCode());
        return ApiResponse.success(Map.of("keyword", keyword, "urls", List.of("https://picsum.photos/seed/" + hashStr + "/400/300")));
    }

    @GetMapping("/step-img")
    public ResponseEntity<String> getStepImage(@RequestParam String q) {
        if (q == null || q.trim().isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        String keyword = q.trim().toLowerCase();
        String svg = generateStepSvg(keyword);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.valueOf("image/svg+xml"));
        headers.setCacheControl("public, max-age=86400");
        return new ResponseEntity<>(svg, headers, HttpStatus.OK);
    }


    private List<String> searchPixabay(String keyword) throws Exception {
        String url = "https://pixabay.com/api/?key=" + imageApiKey
            + "&q=" + URLEncoder.encode(keyword, StandardCharsets.UTF_8)
            + "&image_type=photo&per_page=3&safesearch=true&category=food";
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .GET()
            .build();
        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) return List.of();
        var root = objectMapper.readTree(resp.body());
        List<String> result = new ArrayList<>();
        var hits = root.path("hits");
        for (int i = 0; i < Math.min(3, hits.size()); i++) {
            String imgUrl = hits.get(i).path("webformatURL").asText();
            if (!imgUrl.isEmpty()) result.add(imgUrl);
        }
        return result;
    }
    /** 生成烹饪步骤 SVG 插画 */
    private String generateStepSvg(String keyword) {
        String bgFrom, bgTo, scene;

        if (keyword.matches(".*(cut|chop|dice|slice|mince).*")) {
            bgFrom = "#fef3c7"; bgTo = "#fde68a";
            scene = "<g transform='translate(40,15)'>"
                + "<rect x='30' y='105' width='200' height='18' rx='4' fill='#d4a574'/>"
                + "<rect x='32' y='107' width='196' height='14' rx='3' fill='#e8c494'/>"
                + "<rect x='170' y='50' width='8' height='60' rx='3' fill='#8B4513' transform='rotate(25,174,80)'/>"
                + "<rect x='167' y='45' width='12' height='15' rx='3' fill='#555' transform='rotate(25,173,52)'/>"
                + "<circle cx='110' cy='98' r='14' fill='#ef4444'/>"
                + "<circle cx='110' cy='94' r='14' fill='#dc2626'/>"
                + "<path d='M105 82 Q110 78 115 82' stroke='#22c55e' stroke-width='2' fill='none'/>"
                + "<ellipse cx='70' cy='100' rx='10' ry='5' fill='#ef4444'/>"
                + "<ellipse cx='90' cy='102' rx='10' ry='5' fill='#ef4444'/>"
                + "<text x='80' y='38' font-size='14' fill='#92400e' font-weight='bold' text-anchor='middle'>🔪 切菜</text>"
                + "</g>";
        } else if (keyword.matches(".*(wash|rinse|clean|peel).*")) {
            bgFrom = "#e0f2fe"; bgTo = "#bae6fd";
            scene = "<g transform='translate(40,15)'>"
                + "<ellipse cx='100' cy='120' rx='60' ry='15' fill='#94a3b8'/>"
                + "<path d='M40 105 Q40 125 100 125 Q160 125 160 105' fill='#cbd5e1'/>"
                + "<ellipse cx='100' cy='105' rx='60' ry='12' fill='#e2e8f0'/>"
                + "<path d='M50 105 Q55 95 65 105 Q75 92 85 105 Q95 90 105 105 Q115 92 125 105 Q135 95 145 105 L150 110' stroke='#60a5fa' stroke-width='2' fill='none' opacity='0.6'/>"
                + "<ellipse cx='100' cy='95' rx='30' ry='12' fill='#22c55e'/>"
                + "<ellipse cx='100' cy='90' rx='28' ry='10' fill='#4ade80'/>"
                + "<circle cx='55' cy='60' r='3' fill='#93c5fd'/>"
                + "<circle cx='65' cy='55' r='2' fill='#93c5fd'/>"
                + "<circle cx='145' cy='62' r='2.5' fill='#93c5fd'/>"
                + "<text x='80' y='38' font-size='13' fill='#1e40af' font-weight='bold' text-anchor='middle'>🚿 清洗</text>"
                + "</g>";
        } else if (keyword.matches(".*(fry|stir.?fry|saute|\\bpan\\b).*")) {
            bgFrom = "#fce4ec"; bgTo = "#f8bbd0";
            scene = "<g transform='translate(40,15)'>"
                + "<ellipse cx='100' cy='120' rx='60' ry='10' fill='#333'/>"
                + "<path d='M40 105 Q40 120 100 120 Q160 120 160 105' fill='#555'/>"
                + "<ellipse cx='100' cy='105' rx='58' ry='10' fill='#777'/>"
                + "<ellipse cx='100' cy='102' rx='52' ry='8' fill='#eab308'/>"
                + "<ellipse cx='85' cy='100' rx='20' ry='6' fill='#f59e0b'/>"
                + "<ellipse cx='108' cy='98' rx='15' ry='5' fill='#ef4444'/>"
                + "<circle cx='95' cy='97' r='6' fill='#22c55e'/>"
                + "<rect x='155' y='114' width='30' height='8' rx='4' fill='#444'/>"
                + "<path d='M50 125 Q55 130 60 125' stroke='#f97316' stroke-width='1.5' fill='none' opacity='0.5'/>"
                + "<path d='M70 128 Q75 133 80 128' stroke='#f97316' stroke-width='1.5' fill='none' opacity='0.5'/>"
                + "<path d='M60 90 Q65 80 70 90' stroke='#ddd' stroke-width='1.5' fill='none' opacity='0.5'/>"
                + "<path d='M80 88 Q85 78 90 88' stroke='#ddd' stroke-width='1.5' fill='none' opacity='0.5'/>"
                + "<text x='80' y='38' font-size='14' fill='#9d174d' font-weight='bold' text-anchor='middle'>🍳 炒制</text>"
                + "</g>";
        } else if (keyword.matches(".*(boil|cook|simmer|stew|braise|blanch).*")) {
            bgFrom = "#fff3e0"; bgTo = "#ffe0b2";
            scene = "<g transform='translate(40,15)'>"
                + "<ellipse cx='100' cy='120' rx='50' ry='8' fill='#e65100'/>"
                + "<rect x='52' y='70' width='96' height='50' rx='4' fill='#ff9800'/>"
                + "<rect x='55' y='73' width='90' height='44' rx='3' fill='#ffb74d'/>"
                + "<ellipse cx='100' cy='80' rx='44' ry='8' fill='#ffe0b2' opacity='0.8'/>"
                + "<ellipse cx='90' cy='78' rx='15' ry='5' fill='#f97316'/>"
                + "<ellipse cx='110' cy='76' rx='12' ry='4' fill='#ef4444'/>"
                + "<path d='M60 65 Q65 52 70 65' stroke='#fff' stroke-width='2' fill='none' opacity='0.6'/>"
                + "<path d='M80 62 Q85 48 90 62' stroke='#fff' stroke-width='2' fill='none' opacity='0.5'/>"
                + "<path d='M110 60 Q115 46 120 60' stroke='#fff' stroke-width='2' fill='none' opacity='0.6'/>"
                + "<rect x='40' y='82' width='14' height='6' rx='3' fill='#e65100'/>"
                + "<rect x='146' y='82' width='14' height='6' rx='3' fill='#e65100'/>"
                + "<text x='80' y='38' font-size='14' fill='#9a3412' font-weight='bold' text-anchor='middle'>🥘 炖煮</text>"
                + "</g>";
        } else if (keyword.matches(".*(season|marinate|salt|sugar|sauce|soy).*")) {
            bgFrom = "#f0fdf4"; bgTo = "#bbf7d0";
            scene = "<g transform='translate(40,15)'>"
                + "<ellipse cx='100' cy='120' rx='50' ry='12' fill='#94a3b8'/>"
                + "<path d='M50 110 Q50 120 100 120 Q150 120 150 110' fill='#cbd5e1'/>"
                + "<ellipse cx='100' cy='110' rx='48' ry='10' fill='#e2e8f0'/>"
                + "<ellipse cx='100' cy='107' rx='42' ry='8' fill='#fcd34d'/>"
                + "<path d='M50 45 Q45 35 40 42' stroke='#d4a574' stroke-width='5' fill='none' stroke-linecap='round'/>"
                + "<path d='M40 42 Q35 38 32 45 Q28 50 25 55' stroke='#d4a574' stroke-width='4' fill='none' stroke-linecap='round'/>"
                + "<circle cx='55' cy='68' r='1.5' fill='#94a3b8'/>"
                + "<circle cx='70' cy='65' r='1' fill='#94a3b8'/>"
                + "<circle cx='85' cy='62' r='1.5' fill='#94a3b8'/>"
                + "<circle cx='78' cy='70' r='1.2' fill='#94a3b8'/>"
                + "<text x='80' y='38' font-size='14' fill='#166534' font-weight='bold' text-anchor='middle'>🧂 调味</text>"
                + "</g>";
        } else if (keyword.matches(".*(mix|stir|whisk|beat|blend).*")) {
            bgFrom = "#fdf2f8"; bgTo = "#fbcfe8";
            scene = "<g transform='translate(40,15)'>"
                + "<ellipse cx='100' cy='125' rx='50' ry='10' fill='#94a3b8'/>"
                + "<path d='M50 115 Q50 125 100 125 Q150 125 150 115' fill='#cbd5e1'/>"
                + "<ellipse cx='100' cy='115' rx='48' ry='10' fill='#e2e8f0'/>"
                + "<ellipse cx='90' cy='112' rx='20' ry='7' fill='#fbbf24'/>"
                + "<ellipse cx='110' cy='110' rx='15' ry='5' fill='#f59e0b'/>"
                + "<rect x='96' y='60' width='6' height='50' rx='3' fill='#888'/>"
                + "<circle cx='99' cy='56' r='8' fill='#aaa'/>"
                + "<circle cx='99' cy='56' r='6' fill='#ccc'/>"
                + "<circle cx='62' cy='100' r='2' fill='#fbbf24' opacity='0.6'/>"
                + "<circle cx='135' cy='98' r='1.5' fill='#fbbf24' opacity='0.5'/>"
                + "<text x='80' y='38' font-size='14' fill='#9d174d' font-weight='bold' text-anchor='middle'>🥄 搅拌</text>"
                + "</g>";
        } else if (keyword.matches(".*(meat|ribs|chicken|pork|beef|fish).*")) {
            bgFrom = "#fee2e2"; bgTo = "#fecaca";
            scene = "<g transform='translate(40,15)'>"
                + "<ellipse cx='100' cy='125' rx='65' ry='12' fill='#e2e8f0'/>"
                + "<ellipse cx='100' cy='123' rx='60' ry='10' fill='#f1f5f9'/>"
                + "<rect x='65' y='90' width='70' height='30' rx='8' fill='#ef4444' opacity='0.7'/>"
                + "<rect x='68' y='93' width='64' height='24' rx='6' fill='#fca5a5'/>"
                + "<path d='M75 98 Q85 95 95 100 Q105 96 115 98' stroke='#fef2f2' stroke-width='1.5' fill='none' opacity='0.6'/>"
                + "<path d='M72 108 Q85 105 98 108 Q110 106 122 108' stroke='#fef2f2' stroke-width='1.5' fill='none' opacity='0.5'/>"
                + "<rect x='90' y='82' width='8' height='14' rx='3' fill='#d1d5db'/>"
                + "<rect x='91' y='83' width='6' height='12' rx='2' fill='#e5e7eb'/>"
                + "<text x='80' y='38' font-size='14' fill='#991b1b' font-weight='bold' text-anchor='middle'>🥩 肉类</text>"
                + "</g>";
        } else if (keyword.matches(".*(wrench|screwdriver|pliers|hammer|drill|valve|pipe|faucet|leak|clog|fix|repair|tighten|loosen).*")) {
            bgFrom = "#ede9fe"; bgTo = "#ddd6fe";
            scene = "<g transform='translate(40,15)'>"
                + "<rect x='70' y='90' width='60' height='8' rx='3' fill='#888'/>"
                + "<rect x='85' y='60' width='30' height='32' rx='4' fill='#eab308'/>"
                + "<rect x='75' y='98' width='50' height='20' rx='4' fill='#666'/>"
                + "<circle cx='99' cy='76' r='6' fill='#333'/>"
                + "<rect x='168' y='72' width='8' height='55' rx='3' fill='#d4a574' transform='rotate(15,172,99)'/>"
                + "<rect x='165' y='67' width='12' height='12' rx='2' fill='#888' transform='rotate(15,171,73)'/>"
                + "<circle cx='58' cy='95' r='12' fill='#f87171' opacity='0.6'/>"
                + "<text x='80' y='40' font-size='14' fill='#6d28d9' font-weight='bold' text-anchor='middle'>🔧 修理</text>"
                + "</g>";
        } else if (keyword.matches(".*(clean|scrub|wipe|mop|sweep|vacuum|dust|polish|organize|tidy|stain|spray|bucket|sponge|laundry|fold|iron|wash).*")) {
            bgFrom = "#f0fdfa"; bgTo = "#ccfbf1";
            scene = "<g transform='translate(40,15)'>"
                + "<rect x='55' y='95' width='90' height='25' rx='5' fill='#60a5fa' opacity='0.4'/>"
                + "<rect x='60' y='100' width='80' height='15' rx='3' fill='#93c5fd' opacity='0.6'/>"
                + "<rect x='145' y='90' width='16' height='30' rx='3' fill='#f97316'/>"
                + "<rect x='148' y='88' width='10' height='4' rx='2' fill='#ea580c'/>"
                + "<rect x='78' y='92' width='12' height='8' rx='2' fill='#eab308' opacity='0.8'/>"
                + "<rect x='97' y='88' width='20' height='4' rx='2' fill='#eab308' opacity='0.8'/>"
                + "<circle cx='80' cy='77' r='5' fill='#60a5fa' opacity='0.5'/>"
                + "<circle cx='95' cy='73' r='4' fill='#60a5fa' opacity='0.5'/>"
                + "<circle cx='110' cy='75' r='3' fill='#60a5fa' opacity='0.5'/>"
                + "<text x='80' y='40' font-size='14' fill='#0f766e' font-weight='bold' text-anchor='middle'>🧼 清洁</text>"
                + "</g>";
        } else if (keyword.matches(".*(shirt|tshirt|pants|jeans|shoes|dress|skirt|jacket|coat|\\bhat\\b|cap|belt|tie|scarf|bag|watch|outfit|wear|fashion|style|sneakers|boots|suit|blazer|top|bottom|accessory|hoodie|sweater|knit|chino|cardigan|blouse|vest|leather|cotton|linen|collar|pocket|sleeve|cuff|button|zip|羊毛|羊绒|棉麻|面料|卫衣|针织|衬衫|西装|夹克|风衣|大衣|裤|裙|鞋|帽|袜|配饰|首饰|项链|手表).*")) {
            bgFrom = "#fdf2f8"; bgTo = "#fce7f3";
            scene = "<g transform='translate(40,15)'>"
                + "<path d='M75 55 L75 125 L125 125 L125 55 Z' fill='#3b82f6' opacity='0.7'/>"
                + "<path d='M75 55 L55 75 L65 78 L75 65 L85 78 L95 75 L75 55' fill='#3b82f6' opacity='0.9'/>"
                + "<rect x='60' y='125' width='20' height='15' rx='3' fill='#3b82f6' opacity='0.6'/>"
                + "<rect x='120' y='125' width='20' height='15' rx='3' fill='#3b82f6' opacity='0.6'/>"
                + "<circle cx='99' cy='90' r='5' fill='#fff' opacity='0.5'/>"
                + "<rect x='140' y='95' width='30' height='45' rx='4' fill='#6b7280' opacity='0.6'/>"
                + "<rect x='145' y='135' width='20' height='5' rx='2' fill='#6b7280' opacity='0.5'/>"
                + "<text x='80' y='40' font-size='14' fill='#9d174d' font-weight='bold' text-anchor='middle'>👔 穿搭</text>"
                + "</g>";
        } else if (keyword.matches(".*(fruit|apple|banana|orange|grape|vegetable|tomato|fresh|ripe|sweet|choose|pick|select).*")) {
            bgFrom = "#fef9c3"; bgTo = "#fde68a";
            scene = "<g transform='translate(40,15)'>"
                + "<circle cx='80' cy='95' r='20' fill='#ef4444'/>"
                + "<path d='M75 72 L80 68 L85 72' fill='#22c55e'/>"
                + "<circle cx='115' cy='98' r='18' fill='#f59e0b'/>"
                + "<path d='M111 78 L115 74 L119 78' fill='#22c55e'/>"
                + "<circle cx='65' cy='100' r='5' fill='#ef4444' opacity='0.5'/>"
                + "<circle cx='90' cy='108' r='4' fill='#ef4444' opacity='0.5'/>"
                + "<circle cx='110' cy='108' r='3' fill='#f59e0b' opacity='0.5'/>"
                + "<path d='M140 60 Q145 50 150 60 Q155 50 160 60' stroke='#888' stroke-width='2' fill='none'/>"
                + "<circle cx='150' cy='65' r='8' fill='#fbbf24'/>"
                + "<text x='80' y='40' font-size='14' fill='#92400e' font-weight='bold' text-anchor='middle'>🍎 挑选</text>"
                + "</g>";
        } else if (keyword.matches(".*(medicine|pill|tablet|capsule|thermometer|bandage|first.?aid|hospital|doctor|nurse|vaccine|syringe|blood|pressure|heart|fever|cough|cold|symptom|health|exercise|yoga|vitamin).*")) {
            bgFrom = "#f0fdf4"; bgTo = "#dcfce7";
            scene = "<g transform='translate(40,15)'>"
                + "<rect x='55' y='85' width='30' height='15' rx='4' fill='#ef4444' opacity='0.6'/>"
                + "<rect x='60' y='88' width='20' height='9' rx='2' fill='#fca5a5' opacity='0.8'/>"
                + "<rect x='95' y='83' width='30' height='19' rx='4' fill='#3b82f6' opacity='0.6'/>"
                + "<rect x='100' y='88' width='20' height='9' rx='2' fill='#93c5fd' opacity='0.8'/>"
                + "<rect x='55' y='70' width='60' height='6' rx='3' fill='#94a3b8'/>"
                + "<circle cx='85' cy='72' r='3' fill='#fff'/>"
                + "<circle cx='99' cy='72' r='3' fill='#fff'/>"
                + "<rect x='140' y='80' width='20' height='40' rx='5' fill='#e2e8f0'/>"
                + "<rect x='145' y='85' width='10' height='30' rx='2' fill='#f1f5f9'/>"
                + "<rect x='142' y='75' width='16' height='8' rx='2' fill='#94a3b8'/>"
                + "<text x='80' y='40' font-size='14' fill='#166534' font-weight='bold' text-anchor='middle'>💊 健康</text>"
                + "</g>";
        } else if (keyword.matches(".*(dog|cat|pet|puppy|kitten|feed|brush|bath|walk|leash|collar|bone|treat|toy|litter|vet|groom).*")) {
            bgFrom = "#fef3c7"; bgTo = "#fde68a";
            scene = "<g transform='translate(40,15)'>"
                + "<ellipse cx='85' cy='100' rx='25' ry='20' fill='#d4a574'/>"
                + "<circle cx='85' cy='75' r='18' fill='#d4a574'/>"
                + "<ellipse cx='78' cy='70' rx='6' ry='8' fill='#c4956a'/>"
                + "<ellipse cx='92' cy='70' rx='6' ry='8' fill='#c4956a'/>"
                + "<circle cx='78' cy='70' r='3' fill='#333'/>"
                + "<circle cx='92' cy='70' r='3' fill='#333'/>"
                + "<ellipse cx='85' cy='82' rx='3' ry='4' fill='#333'/>"
                + "<path d='M95 63 Q100 58 105 63' stroke='#333' stroke-width='2' fill='none'/>"
                + "<ellipse cx='110' cy='110' rx='20' ry='15' fill='#d4a574'/>"
                + "<circle cx='110' cy='97' r='12' fill='#d4a574'/>"
                + "<text x='80' y='40' font-size='14' fill='#92400e' font-weight='bold' text-anchor='middle'>🐶 宠物</text>"
                + "</g>";
        } else {
            bgFrom = "#ecfdf5"; bgTo = "#a7f3d0";
            scene = "<g transform='translate(40,15)'>"
                + "<circle cx='80' cy='90' r='50' fill='#22c55e' opacity='0.1'/>"
                + "<text x='80' y='98' font-size='36' text-anchor='middle'>🍆</text>"
                + "<text x='80' y='38' font-size='14' fill='#166534' font-weight='bold' text-anchor='middle'>👨‍🍳 通用</text>"
                + "</g>";
        }
        return "<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 400 180' width='400' height='180'>"
            + "<defs><linearGradient id='bg' x1='0%' y1='0%' x2='100%' y2='100%'>"
            + "<stop offset='0%' style='stop-color:" + bgFrom + "'/>"
            + "<stop offset='100%' style='stop-color:" + bgTo + "'/></linearGradient></defs>"
            + "<rect width='400' height='180' fill='url(#bg)' rx='12'/>"
            + scene
            + "</svg>";
    }
}
