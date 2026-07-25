package com.timbernest.admin;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
public class AuditLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long adminUserId;
    private String action;
    @Column(length = 4000)
    private String details;
    private Instant createdAt = Instant.now();

    public static AuditLog of(Long adminId, String action, String details) {
        AuditLog log = new AuditLog();
        log.adminUserId = adminId;
        log.action = action;
        log.details = details;
        return log;
    }

    public Long getId() { return id; }
    public Long getAdminUserId() { return adminUserId; }
    public String getAction() { return action; }
    public String getDetails() { return details; }
    public Instant getCreatedAt() { return createdAt; }
}
