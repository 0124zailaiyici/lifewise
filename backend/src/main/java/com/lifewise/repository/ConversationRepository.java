package com.lifewise.repository;

import com.lifewise.entity.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {
    List<Conversation> findByUserIdOrderByCreatedAtDesc(Long userId);
    List<Conversation> findByUserIdAndSceneOrderByCreatedAtDesc(Long userId, String scene);
}
