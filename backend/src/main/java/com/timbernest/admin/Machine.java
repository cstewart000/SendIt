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
}
