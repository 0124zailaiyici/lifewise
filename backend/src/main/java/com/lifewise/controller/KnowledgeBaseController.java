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

    @PostMapping
    public ApiResponse<?> add(@RequestAttribute Long userId,
                              @RequestBody Map<String, String> body) {
        String question = body.get("question");
        String answer = body.get("answer");
        String scene = body.get("scene");
        if (question == null || question.trim().isEmpty()) {
            return ApiResponse.error("问题不能为空");
        }
        if (answer == null || answer.trim().isEmpty()) {
            return ApiResponse.error("回答不能为空");
        }
        knowledgeBaseService.saveAnswer(question.trim(), answer.trim(), scene, userId);
        return ApiResponse.success("已加入常识库");
    }

    @GetMapping("/search")
    public ApiResponse<?> search(@RequestAttribute Long userId,
                                  @RequestParam(required = false) String keyword,
                                  @RequestParam(required = false) String scene) {
        return ApiResponse.success(knowledgeBaseService.search(keyword, scene, userId));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<?> delete(@RequestAttribute Long userId,
                                 @PathVariable Long id) {
        knowledgeBaseService.delete(id, userId);
        return ApiResponse.success("已删除");
    }

    
    @PutMapping("/{id}")
    public ApiResponse<?> update(@RequestAttribute Long userId,
                                  @PathVariable Long id,
                                  @RequestBody Map<String, String> body) {
        String question = body.get("question");
        String answer = body.get("answer");
        String scene = body.get("scene");
        knowledgeBaseService.update(id, userId, question, answer, scene);
        return ApiResponse.success("已保存");
    }

    @PostMapping("/{id}/helpful")
    public ApiResponse<?> markHelpful(@RequestAttribute Long userId,
                                      @PathVariable Long id) {
        knowledgeBaseService.markHelpful(id, userId);
        return ApiResponse.success("感谢反馈");
    }
    @GetMapping("/export")
    public ApiResponse<?> exportAll(@RequestAttribute Long userId) {
        return ApiResponse.success(knowledgeBaseService.exportAll(userId));
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
