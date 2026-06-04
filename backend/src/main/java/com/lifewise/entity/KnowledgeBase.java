package com.lifewise.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "knowledge_base")
public class KnowledgeBase {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 20)
    private String scene;

    @Column(length = 200)
    private String question;

    @Column(columnDefinition = "TEXT")
    private String answer;

    @Column(length = 200)
    private String tags;

    @Column(name = "helpful_count")
    private Integer helpfulCount = 0;

    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
