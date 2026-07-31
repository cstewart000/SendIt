package com.timbernest.cam;

import java.util.ArrayList;
import java.util.List;

/** G-code text plus structured paths / fixings / tabs for UI visualisation. */
public class ToolpathResult {
    private String gcode = "";
    private double sheetWidth;
    private double sheetHeight;
    private int sheetCount;
    private double toolDiameterMm;
    private String toolName;
    private double fixingHoleDiameterMm = 4;
    private double fixingMinToolDistanceMm = 10;
    private double tabWidthMm = 5;
    private double tabHeightMm = 1.5;
    private boolean tabsEnabled = true;
    private boolean fixingsEnabled = true;
    private List<Path> paths = new ArrayList<>();
    private List<Fixing> fixings = new ArrayList<>();
    private List<Tab> tabs = new ArrayList<>();

    public record Pt(double x, double y) {}

    /**
     * @param kind rapid | cut | plunge | drill
     */
    public record Path(String kind, boolean hole, int sheetIndex, String label, List<Pt> points) {}

    public record Fixing(String id, int sheetIndex, double x, double y, double diameterMm,
                         boolean enabled, String label) {}

    public record Tab(String id, int sheetIndex, double x, double y,
                      double widthMm, double heightMm, List<Pt> segment) {}

    public String getGcode() { return gcode; }
    public void setGcode(String gcode) { this.gcode = gcode; }
    public double getSheetWidth() { return sheetWidth; }
    public void setSheetWidth(double sheetWidth) { this.sheetWidth = sheetWidth; }
    public double getSheetHeight() { return sheetHeight; }
    public void setSheetHeight(double sheetHeight) { this.sheetHeight = sheetHeight; }
    public int getSheetCount() { return sheetCount; }
    public void setSheetCount(int sheetCount) { this.sheetCount = sheetCount; }
    public double getToolDiameterMm() { return toolDiameterMm; }
    public void setToolDiameterMm(double toolDiameterMm) { this.toolDiameterMm = toolDiameterMm; }
    public String getToolName() { return toolName; }
    public void setToolName(String toolName) { this.toolName = toolName; }
    public double getFixingHoleDiameterMm() { return fixingHoleDiameterMm; }
    public void setFixingHoleDiameterMm(double v) { this.fixingHoleDiameterMm = v; }
    public double getFixingMinToolDistanceMm() { return fixingMinToolDistanceMm; }
    public void setFixingMinToolDistanceMm(double v) { this.fixingMinToolDistanceMm = v; }
    public double getTabWidthMm() { return tabWidthMm; }
    public void setTabWidthMm(double tabWidthMm) { this.tabWidthMm = tabWidthMm; }
    public double getTabHeightMm() { return tabHeightMm; }
    public void setTabHeightMm(double tabHeightMm) { this.tabHeightMm = tabHeightMm; }
    public boolean isTabsEnabled() { return tabsEnabled; }
    public void setTabsEnabled(boolean tabsEnabled) { this.tabsEnabled = tabsEnabled; }
    public boolean isFixingsEnabled() { return fixingsEnabled; }
    public void setFixingsEnabled(boolean fixingsEnabled) { this.fixingsEnabled = fixingsEnabled; }
    public List<Path> getPaths() { return paths; }
    public void setPaths(List<Path> paths) { this.paths = paths; }
    public List<Fixing> getFixings() { return fixings; }
    public void setFixings(List<Fixing> fixings) { this.fixings = fixings; }
    public List<Tab> getTabs() { return tabs; }
    public void setTabs(List<Tab> tabs) { this.tabs = tabs; }
}
