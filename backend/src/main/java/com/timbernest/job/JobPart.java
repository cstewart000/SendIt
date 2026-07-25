package com.timbernest.job;

import jakarta.persistence.*;

@Entity
public class JobPart {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long jobId;
    private Long designVersionId;
    private String label;
    private int quantity = 1;
    private boolean grainSensitive;
    private double widthMm;
    private double heightMm;

    public Long getId() { return id; }
    public Long getJobId() { return jobId; }
    public void setJobId(Long jobId) { this.jobId = jobId; }
    public Long getDesignVersionId() { return designVersionId; }
    public void setDesignVersionId(Long designVersionId) { this.designVersionId = designVersionId; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public boolean isGrainSensitive() { return grainSensitive; }
    public void setGrainSensitive(boolean grainSensitive) { this.grainSensitive = grainSensitive; }
    public double getWidthMm() { return widthMm; }
    public void setWidthMm(double widthMm) { this.widthMm = widthMm; }
    public double getHeightMm() { return heightMm; }
    public void setHeightMm(double heightMm) { this.heightMm = heightMm; }
}
