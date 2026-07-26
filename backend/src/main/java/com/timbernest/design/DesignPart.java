package com.timbernest.design;

import jakarta.persistence.*;

@Entity
public class DesignPart {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long designVersionId;
    private int partIndex;
    private String label;
    private String contourId;
    @Column(columnDefinition = "TEXT")
    private String geometryJson;
    private double widthMm;
    private double heightMm;

    public Long getId() { return id; }
    public Long getDesignVersionId() { return designVersionId; }
    public void setDesignVersionId(Long designVersionId) { this.designVersionId = designVersionId; }
    public int getPartIndex() { return partIndex; }
    public void setPartIndex(int partIndex) { this.partIndex = partIndex; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public String getContourId() { return contourId; }
    public void setContourId(String contourId) { this.contourId = contourId; }
    public String getGeometryJson() { return geometryJson; }
    public void setGeometryJson(String geometryJson) { this.geometryJson = geometryJson; }
    public double getWidthMm() { return widthMm; }
    public void setWidthMm(double widthMm) { this.widthMm = widthMm; }
    public double getHeightMm() { return heightMm; }
    public void setHeightMm(double heightMm) { this.heightMm = heightMm; }
}
