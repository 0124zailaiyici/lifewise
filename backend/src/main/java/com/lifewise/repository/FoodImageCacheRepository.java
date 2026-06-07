package com.lifewise.repository;

import com.lifewise.entity.FoodImageCache;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FoodImageCacheRepository extends JpaRepository<FoodImageCache, Long> {
    Optional<FoodImageCache> findByDishName(String dishName);
    boolean existsByDishName(String dishName);
}
