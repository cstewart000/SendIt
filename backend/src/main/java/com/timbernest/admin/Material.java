package com.timbernest.admin;

import jakarta.persistence.*;

@Entity
public class Material {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    private double thicknessMm = 18;
    private double sheetWidthMm = 2440;
    private double sheetHeightMm = 1220;
    private double costPerSheet = 85;
    private double densityKgM3 = 600;
    private double scrapFactor = 1.15;
    private double thinWallThresholdMm = 8;
    private double minFeatureMm = 3;

    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public double getThicknessMm() { return thicknessMm; }
    public void setThicknessMm(double thicknessMm) { this.thicknessMm = thicknessMm; }
    public double getSheetWidthMm() { return sheetWidthMm; }
    public void setSheetWidthMm(double sheetWidthMm) { this.sheetWidthMm = sheetWidthMm; }
    public double getSheetHeightMm() { return sheetHeightMm; }
    public void setSheetHeightMm(double sheetHeightMm) { this.sheetHeightMm = sheetHeightMm; }
    public double getCostPerSheet() { return costPerSheet; }
    public void setCostPerSheet(double costPerSheet) { this.costPerSheet = costPerSheet; }
    public double getDensityKgM3() { return densityKgM3; }
    public void setDensityKgM3(double densityKgM3) { this.densityKgM3 = densityKgM3; }
    public double getScrapFactor() { return scrapFactor; }
    public void setScrapFactor(double scrapFactor) { this.scrapFactor = scrapFactor; }
    public double getThinWallThresholdMm() { return thinWallThresholdMm; }
    public void setThinWallThresholdMm(double v) { this.thinWallThresholdMm = v; }
    public double getMinFeatureMm() { return minFeatureMm; }
    public void setMinFeatureMm(double minFeatureMm) { this.minFeatureMm = minFeatureMm; }
}
