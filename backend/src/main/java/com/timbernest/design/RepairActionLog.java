package com.timbernest.design;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
public class RepairActionLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long designVersionId;
    private String action;
    @Column(length = 2000)
    private String reason;
    private Instant createdAt = Instant.now();

    public static RepairActionLog of(Long versionId, String action, String reason) {
        RepairActionLog log = new RepairActionLog();
        log.designVersionId = versionId;
        log.action = action;
        log.reason = reason;
        return log;
    }

    public Long getId() { return id; }
    public Long getDesignVersionId() { return designVersionId; }
    public String getAction() { return action; }
    public String getReason() { return reason; }
    public Instant getCreatedAt() { return createdAt; }
}
