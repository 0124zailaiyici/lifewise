package com.lifewise.repository;

import com.lifewise.entity.KnowledgeBase;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface KnowledgeBaseRepository extends JpaRepository<KnowledgeBase, Long> {
    List<KnowledgeBase> findBySceneOrderByHelpfulCountDesc(String scene);
    List<KnowledgeBase> findByQuestionContaining(String keyword);
}
