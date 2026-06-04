package com.lifewise.repository;

import com.lifewise.entity.Favorite;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface FavoriteRepository extends JpaRepository<Favorite, Long> {
    List<Favorite> findByUserIdOrderByCreatedAtDesc(Long userId);
    Optional<Favorite> findByUserIdAndMessageId(Long userId, Long messageId);
    boolean existsByUserIdAndMessageId(Long userId, Long messageId);
    void deleteByUserIdAndMessageId(Long userId, Long messageId);
}
