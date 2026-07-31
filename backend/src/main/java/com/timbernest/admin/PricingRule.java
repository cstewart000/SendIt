package com.timbernest.admin;

import jakarta.persistence.*;

@Entity
public class PricingRule {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long machineId;
    private String name;
    private String ruleKey;
    /** Quoted: bare VALUE is reserved in H2 and some SQL dialects. */
    @Column(name = "\"value\"")
    private double value;
    private String description;

    public Long getId() { return id; }
    public Long getMachineId() { return machineId; }
    public void setMachineId(Long machineId) { this.machineId = machineId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getRuleKey() { return ruleKey; }
    public void setRuleKey(String ruleKey) { this.ruleKey = ruleKey; }
    public double getValue() { return value; }
    public void setValue(double value) { this.value = value; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
