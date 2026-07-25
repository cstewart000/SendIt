package com.timbernest.admin;

import jakarta.persistence.*;

@Entity
public class Tool {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long machineId;
    private String name;
    private String type = "ENDMILL";
    private double diameterMm = 6;
    private int fluteCount = 2;
    private double maxDepthMm = 20;
    private double wearCharge = 2.5;

    public Long getId() { return id; }
    public Long getMachineId() { return machineId; }
    public void setMachineId(Long machineId) { this.machineId = machineId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public double getDiameterMm() { return diameterMm; }
    public void setDiameterMm(double diameterMm) { this.diameterMm = diameterMm; }
    public int getFluteCount() { return fluteCount; }
    public void setFluteCount(int fluteCount) { this.fluteCount = fluteCount; }
    public double getMaxDepthMm() { return maxDepthMm; }
    public void setMaxDepthMm(double maxDepthMm) { this.maxDepthMm = maxDepthMm; }
    public double getWearCharge() { return wearCharge; }
    public void setWearCharge(double wearCharge) { this.wearCharge = wearCharge; }
}
