package com.lifewise.controller;

import com.lifewise.common.ApiResponse;
import com.lifewise.service.KnowledgeBaseService;
import lombok.RequiredArgsConstructor;
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

    @PostMapping("/{id}/helpful")
    public ApiResponse<?> markHelpful(@PathVariable Long id) {
        knowledgeBaseService.markHelpful(id);
        return ApiResponse.success("感谢反馈");
    }
}
