package com.timbernest.nesting;

import java.util.ArrayList;
import java.util.List;

public class NestResult {
    private double sheetWidth;
    private double sheetHeight;
    private double margin;
    private double gap;
    private int sheetCount;
    private List<NestPlacement> placements = new ArrayList<>();

    public double getSheetWidth() { return sheetWidth; }
    public void setSheetWidth(double sheetWidth) { this.sheetWidth = sheetWidth; }
    public double getSheetHeight() { return sheetHeight; }
    public void setSheetHeight(double sheetHeight) { this.sheetHeight = sheetHeight; }
    public double getMargin() { return margin; }
    public void setMargin(double margin) { this.margin = margin; }
    public double getGap() { return gap; }
    public void setGap(double gap) { this.gap = gap; }
    public int getSheetCount() { return sheetCount; }
    public void setSheetCount(int sheetCount) { this.sheetCount = sheetCount; }
    public List<NestPlacement> getPlacements() { return placements; }
    public void setPlacements(List<NestPlacement> placements) { this.placements = placements; }
}
