package com.timbernest.job;

import com.timbernest.common.JobStatus;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "jobs")
public class Job {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long ownerId;
    private Long machineId;
    private Long materialId;
    private Long toolId;
    @Enumerated(EnumType.STRING)
    private JobStatus status = JobStatus.DRAFT;
    private boolean nestingLocked;
    private double marginMm = 10;
    private double partGapMm = 8;
    @Column(columnDefinition = "TEXT")
    private String nestingJson = "{}";
    @Column(columnDefinition = "TEXT")
    private String quoteJson;
    private String gcodePath;
    private String setupSheetPath;
    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();

    public Long getId() { return id; }
    public Long getOwnerId() { return ownerId; }
    public void setOwnerId(Long ownerId) { this.ownerId = ownerId; }
    public Long getMachineId() { return machineId; }
    public void setMachineId(Long machineId) { this.machineId = machineId; }
    public Long getMaterialId() { return materialId; }
    public void setMaterialId(Long materialId) { this.materialId = materialId; }
    public Long getToolId() { return toolId; }
    public void setToolId(Long toolId) { this.toolId = toolId; }
    public JobStatus getStatus() { return status; }
    public void setStatus(JobStatus status) { this.status = status; }
    public boolean isNestingLocked() { return nestingLocked; }
    public void setNestingLocked(boolean nestingLocked) { this.nestingLocked = nestingLocked; }
    public double getMarginMm() { return marginMm; }
    public void setMarginMm(double marginMm) { this.marginMm = marginMm; }
    public double getPartGapMm() { return partGapMm; }
    public void setPartGapMm(double partGapMm) { this.partGapMm = partGapMm; }
    public String getNestingJson() { return nestingJson; }
    public void setNestingJson(String nestingJson) { this.nestingJson = nestingJson; }
    public String getQuoteJson() { return quoteJson; }
    public void setQuoteJson(String quoteJson) { this.quoteJson = quoteJson; }
    public String getGcodePath() { return gcodePath; }
    public void setGcodePath(String gcodePath) { this.gcodePath = gcodePath; }
    public String getSetupSheetPath() { return setupSheetPath; }
    public void setSetupSheetPath(String setupSheetPath) { this.setupSheetPath = setupSheetPath; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void touch() { this.updatedAt = Instant.now(); }
}
