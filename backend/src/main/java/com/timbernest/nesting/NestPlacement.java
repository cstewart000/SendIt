package com.timbernest.nesting;

public class NestPlacement {
    private Long jobPartId;
    private String label;
    private int sheetIndex;
    private double x;
    private double y;
    private double width;
    private double height;
    /** Unrotated part size (mm); width/height are the rotated AABB. */
    private double nativeWidth;
    private double nativeHeight;
    private double rotationDeg;
    private boolean grainSensitive;

    public Long getJobPartId() { return jobPartId; }
    public void setJobPartId(Long jobPartId) { this.jobPartId = jobPartId; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public int getSheetIndex() { return sheetIndex; }
    public void setSheetIndex(int sheetIndex) { this.sheetIndex = sheetIndex; }
    public double getX() { return x; }
    public void setX(double x) { this.x = x; }
    public double getY() { return y; }
    public void setY(double y) { this.y = y; }
    public double getWidth() { return width; }
    public void setWidth(double width) { this.width = width; }
    public double getHeight() { return height; }
    public void setHeight(double height) { this.height = height; }
    public double getNativeWidth() { return nativeWidth; }
    public void setNativeWidth(double nativeWidth) { this.nativeWidth = nativeWidth; }
    public double getNativeHeight() { return nativeHeight; }
    public void setNativeHeight(double nativeHeight) { this.nativeHeight = nativeHeight; }
    public double getRotationDeg() { return rotationDeg; }
    public void setRotationDeg(double rotationDeg) { this.rotationDeg = rotationDeg; }
    public boolean isGrainSensitive() { return grainSensitive; }
    public void setGrainSensitive(boolean grainSensitive) { this.grainSensitive = grainSensitive; }
}
