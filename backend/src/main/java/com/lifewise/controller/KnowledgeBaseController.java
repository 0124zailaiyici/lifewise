package com.lifewise.controller;

import com.lifewise.common.ApiResponse;
import com.lifewise.service.KnowledgeBaseService;
import lombok.RequiredArgsConstructor;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/kb")
@RequiredArgsConstructor
public class KnowledgeBaseController {

    private final KnowledgeBaseService knowledgeBaseService;

    @GetMapping("/search")
    public ApiResponse<?> search(@RequestParam String keyword,
                                  @RequestParam(required = false) String scene) {
        return ApiResponse.success(knowledgeBaseService.search(keyword, scene));
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
}
