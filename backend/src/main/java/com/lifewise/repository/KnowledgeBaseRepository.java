package com.lifewise.repository;

import com.lifewise.entity.KnowledgeBase;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface KnowledgeBaseRepository extends JpaRepository<KnowledgeBase, Long> {
    List<KnowledgeBase> findBySceneAndUserIdOrderByHelpfulCountDesc(String scene, Long userId);
    List<KnowledgeBase> findByUserId(Long userId);
    List<KnowledgeBase> findBySceneOrderByHelpfulCountDesc(String scene);
    List<KnowledgeBase> findByQuestionContaining(String keyword);
}
