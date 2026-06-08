package com.lifewise.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "ai_call_audit")
public class AiCallAudit {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(length = 30)
    private String provider;

    @Column(length = 80)
    private String model;

    @Column(length = 30)
    private String scene;

    @Column(length = 30)
    private String status;

    @Column(length = 300)
    private String detail;

    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
