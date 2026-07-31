package com.timbernest.admin;

import jakarta.persistence.*;

@Entity
public class Machine {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    private double workXmm = 2440;
    private double workYmm = 1220;
    private double workZmm = 100;
    private String postProcessor = "LinuxCNC";
    private double defaultFeedMmMin = 3000;
    private double defaultSpeedRpm = 18000;
    private double hourlyRate = 60;
    /** Nest kerf / clearance (mm): min spacing between parts and sheet edge. */
    private double kerfMm = 8;
    /**
     * Min distance (mm) from fixing-hole centre to the edge of the profile toolpath
     * (tool centreline offset by tool radius). Default 10 mm.
     */
    private double fixingMinToolDistanceMm = 10;
    /** Wood-screw pilot / clearance drill diameter (mm). */
    private double fixingHoleDiameterMm = 4;
    private boolean fixingsEnabled = true;
    private boolean tabsEnabled = true;
    /** Uncut tab length along the outer profile (mm). */
    private double tabWidthMm = 5;
    /** Material left under each tab (mm); final depth cut skips this thickness at tabs. */
    private double tabHeightMm = 1.5;
    /** Preferred number of tabs per outer profile (clamped by perimeter). */
    private int tabCount = 4;

    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public double getWorkXmm() { return workXmm; }
    public void setWorkXmm(double workXmm) { this.workXmm = workXmm; }
    public double getWorkYmm() { return workYmm; }
    public void setWorkYmm(double workYmm) { this.workYmm = workYmm; }
    public double getWorkZmm() { return workZmm; }
    public void setWorkZmm(double workZmm) { this.workZmm = workZmm; }
    public String getPostProcessor() { return postProcessor; }
    public void setPostProcessor(String postProcessor) { this.postProcessor = postProcessor; }
    public double getDefaultFeedMmMin() { return defaultFeedMmMin; }
    public void setDefaultFeedMmMin(double defaultFeedMmMin) { this.defaultFeedMmMin = defaultFeedMmMin; }
    public double getDefaultSpeedRpm() { return defaultSpeedRpm; }
    public void setDefaultSpeedRpm(double defaultSpeedRpm) { this.defaultSpeedRpm = defaultSpeedRpm; }
    public double getHourlyRate() { return hourlyRate; }
    public void setHourlyRate(double hourlyRate) { this.hourlyRate = hourlyRate; }
    public double getKerfMm() { return kerfMm; }
    public void setKerfMm(double kerfMm) { this.kerfMm = kerfMm; }
    public double getFixingMinToolDistanceMm() { return fixingMinToolDistanceMm; }
    public void setFixingMinToolDistanceMm(double v) { this.fixingMinToolDistanceMm = v; }
    public double getFixingHoleDiameterMm() { return fixingHoleDiameterMm; }
    public void setFixingHoleDiameterMm(double v) { this.fixingHoleDiameterMm = v; }
    public boolean isFixingsEnabled() { return fixingsEnabled; }
    public void setFixingsEnabled(boolean fixingsEnabled) { this.fixingsEnabled = fixingsEnabled; }
    public boolean isTabsEnabled() { return tabsEnabled; }
    public void setTabsEnabled(boolean tabsEnabled) { this.tabsEnabled = tabsEnabled; }
    public double getTabWidthMm() { return tabWidthMm; }
    public void setTabWidthMm(double tabWidthMm) { this.tabWidthMm = tabWidthMm; }
    public double getTabHeightMm() { return tabHeightMm; }
    public void setTabHeightMm(double tabHeightMm) { this.tabHeightMm = tabHeightMm; }
    public int getTabCount() { return tabCount; }
    public void setTabCount(int tabCount) { this.tabCount = tabCount; }
}
