package com.lifewise.service;

import com.lifewise.entity.KnowledgeBase;
import java.util.List;

public interface KnowledgeBaseService {
    String findAnswer(String question, String scene);
    void saveAnswer(String question, String answer, String scene);
    List<KnowledgeBase> search(String keyword, String scene);
    void markHelpful(Long id);
    void delete(Long id);
}
