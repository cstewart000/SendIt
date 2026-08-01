package com.timbernest.design;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
public class DesignVersion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long designId;
    private int versionNumber;
    private String originalFilename;
    private String originalPath;
    private String repairedPath;
    @Column(columnDefinition = "TEXT")
    private String geometryJson;
    /** Geometry snapshot taken immediately before the last dog-bone apply (for undo). */
    @Column(columnDefinition = "TEXT")
    private String preDogboneGeometryJson;
    @Column(columnDefinition = "TEXT")
    private String issuesJson = "[]";
    private boolean analysed;
    private boolean repaired;
    private boolean warningsAcknowledged;
    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public Long getDesignId() { return designId; }
    public void setDesignId(Long designId) { this.designId = designId; }
    public int getVersionNumber() { return versionNumber; }
    public void setVersionNumber(int versionNumber) { this.versionNumber = versionNumber; }
    public String getOriginalFilename() { return originalFilename; }
    public void setOriginalFilename(String originalFilename) { this.originalFilename = originalFilename; }
    public String getOriginalPath() { return originalPath; }
    public void setOriginalPath(String originalPath) { this.originalPath = originalPath; }
    public String getRepairedPath() { return repairedPath; }
    public void setRepairedPath(String repairedPath) { this.repairedPath = repairedPath; }
    public String getGeometryJson() { return geometryJson; }
    public void setGeometryJson(String geometryJson) { this.geometryJson = geometryJson; }
    public String getPreDogboneGeometryJson() { return preDogboneGeometryJson; }
    public void setPreDogboneGeometryJson(String preDogboneGeometryJson) {
        this.preDogboneGeometryJson = preDogboneGeometryJson;
    }
    public boolean hasPreDogboneSnapshot() {
        return preDogboneGeometryJson != null && !preDogboneGeometryJson.isBlank();
    }
    public String getIssuesJson() { return issuesJson; }
    public void setIssuesJson(String issuesJson) { this.issuesJson = issuesJson; }
    public boolean isAnalysed() { return analysed; }
    public void setAnalysed(boolean analysed) { this.analysed = analysed; }
    public boolean isRepaired() { return repaired; }
    public void setRepaired(boolean repaired) { this.repaired = repaired; }
    public boolean isWarningsAcknowledged() { return warningsAcknowledged; }
    public void setWarningsAcknowledged(boolean warningsAcknowledged) {
        this.warningsAcknowledged = warningsAcknowledged;
    }
    public Instant getCreatedAt() { return createdAt; }
}
