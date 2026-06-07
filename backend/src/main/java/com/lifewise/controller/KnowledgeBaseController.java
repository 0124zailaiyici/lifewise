package com.lifewise.controller;

import com.lifewise.common.ApiResponse;
import com.lifewise.entity.KnowledgeBase;
import com.lifewise.service.KnowledgeBaseService;
import lombok.RequiredArgsConstructor;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/kb")
@RequiredArgsConstructor
public class KnowledgeBaseController {

    private final KnowledgeBaseService knowledgeBaseService;

    @GetMapping("/search")
    public ApiResponse<?> search(@RequestAttribute Long userId,
                                  @RequestParam(required = false) String keyword,
                                  @RequestParam(required = false) String scene) {
        return ApiResponse.success(knowledgeBaseService.search(keyword, scene, userId));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<?> delete(@PathVariable Long id) {
        knowledgeBaseService.delete(id);
        return ApiResponse.success("???");
    }

    
    @PutMapping("/{id}")
    public ApiResponse<?> update(@PathVariable Long id,
                                  @RequestBody Map<String, String> body) {
        String question = body.get("question");
        String answer = body.get("answer");
        String scene = body.get("scene");
        knowledgeBaseService.update(id, question, answer, scene);
        return ApiResponse.success("????");
    }

    @PostMapping("/{id}/helpful")
    public ApiResponse<?> markHelpful(@PathVariable Long id) {
        knowledgeBaseService.markHelpful(id);
        return ApiResponse.success("感谢反馈");
    }
    @GetMapping("/export")
    public ApiResponse<?> exportAll() {
        return ApiResponse.success(knowledgeBaseService.exportAll());
    }

    @PostMapping("/import")
    public ApiResponse<?> importAll(@RequestAttribute Long userId, @RequestBody List<Map<String, Object>> items) {
        List<KnowledgeBase> list = items.stream().map(m -> {
            KnowledgeBase kb = new KnowledgeBase();
            kb.setUserId(userId);
            kb.setQuestion((String) m.get("question"));
            kb.setAnswer((String) m.get("answer"));
            kb.setScene((String) m.get("scene"));
            kb.setTags((String) m.get("tags"));
            return kb;
        }).collect(java.util.stream.Collectors.toList());
        int count = knowledgeBaseService.importAll(list);
        return ApiResponse.success("导入成功：" + count + " 条");
    }

}