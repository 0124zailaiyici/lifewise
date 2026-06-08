package com.lifewise.service;

import com.lifewise.entity.KnowledgeBase;
import java.util.List;

public interface KnowledgeBaseService {
    String findAnswer(String question, String scene, Long userId);
    void saveAnswer(String question, String answer, String scene, Long userId);
    List<KnowledgeBase> search(String keyword, String scene, Long userId);
    void markHelpful(Long id, Long userId);
    void delete(Long id, Long userId);
    void update(Long id, Long userId, String question, String answer, String scene);
    List<KnowledgeBase> exportAll(Long userId);
    int importAll(List<KnowledgeBase> items);
}

