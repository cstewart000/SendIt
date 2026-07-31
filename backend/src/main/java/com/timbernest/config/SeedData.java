package com.timbernest.config;

import com.timbernest.admin.*;
import com.timbernest.user.AppUser;
import com.timbernest.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class SeedData {
    private static final Logger log = LoggerFactory.getLogger(SeedData.class);

    @Bean
    @Order(10)
    @ConditionalOnProperty(name = "sendit.seed", havingValue = "true", matchIfMissing = true)
    ApplicationRunner seed(UserRepository users, MachineRepository machines, ToolRepository tools,
                           MaterialRepository materials, ProcessRepository processes,
                           PricingRuleRepository pricing, PasswordEncoder encoder) {
        return args -> {
            if (users.count() == 0) {
                AppUser admin = new AppUser();
                admin.setEmail("admin@sendit.local");
                admin.setPasswordHash(encoder.encode("admin12345"));
                admin.setName("Admin");
                admin.setRole("ADMIN");
                users.save(admin);
                AppUser hobby = new AppUser();
                hobby.setEmail("hobby@sendit.local");
                hobby.setPasswordHash(encoder.encode("hobby12345"));
                hobby.setName("Hobby User");
                hobby.setRole("USER");
                users.save(hobby);
                log.info("Seeded users admin@sendit.local / hobby@sendit.local");
            }
            if (machines.count() == 0) seedShop(machines, tools, materials, processes, pricing);
            backfillPricing(machines, pricing);
            ensureHobbyTools(machines, tools);
            ensureKerfDefaults(machines);
        };
    }

    private void backfillPricing(MachineRepository machines, PricingRuleRepository pricing) {
        if (machines.count() == 0) return;
        Long mid = machines.findAll().get(0).getId();
        int n = 0;
        for (PricingRule r : pricing.findAll()) {
            if (r.getMachineId() == null) {
                r.setMachineId(mid);
                pricing.save(r);
                n++;
            }
        }
        if (n > 0) log.info("Backfilled {} pricing rules onto machineId={}", n, mid);
    }

    /** If a machine has fewer than 3 tools, add a typical hobby CNC kit. */
    private void ensureHobbyTools(MachineRepository machines, ToolRepository tools) {
        for (Machine m : machines.findAll()) {
            long count = tools.findByMachineId(m.getId()).size();
            if (count >= 3) continue;
            seedHobbyTools(tools, m.getId());
            log.info("Seeded hobby tool kit on machineId={} (had {} tools)", m.getId(), count);
        }
    }

    private void ensureKerfDefaults(MachineRepository machines) {
        for (Machine m : machines.findAll()) {
            boolean dirty = false;
            if (m.getKerfMm() <= 0) { m.setKerfMm(8); dirty = true; }
            if (m.getFixingMinToolDistanceMm() <= 0) { m.setFixingMinToolDistanceMm(10); dirty = true; }
            if (m.getFixingHoleDiameterMm() <= 0) { m.setFixingHoleDiameterMm(4); dirty = true; }
            if (m.getTabWidthMm() <= 0) { m.setTabWidthMm(5); dirty = true; }
            if (m.getTabHeightMm() <= 0) { m.setTabHeightMm(1.5); dirty = true; }
            if (m.getTabCount() <= 0) { m.setTabCount(4); dirty = true; }
            if (dirty) machines.save(m);
        }
    }

    private void seedShop(MachineRepository machines, ToolRepository tools, MaterialRepository materials,
                          ProcessRepository processes, PricingRuleRepository pricing) {
        Machine m = new Machine();
        m.setName("Shop Router 2440");
        m.setKerfMm(8);
        machines.save(m);
        seedHobbyTools(tools, m.getId());
        Material mat = new Material();
        mat.setName("Plywood 18mm");
        materials.save(mat);
        ProcessDef p = new ProcessDef();
        p.setName("CNC Profile Plywood");
        p.setMachineId(m.getId());
        p.setMaterialId(mat.getId());
        processes.save(p);
        saveRule(pricing, m.getId(), "SETUP_FEE", "Setup fee", 25);
        saveRule(pricing, m.getId(), "MIN_ORDER", "Minimum order", 40);
        saveRule(pricing, m.getId(), "MATERIAL_MARKUP", "Material markup multiplier", 1.0);
        log.info("Seeded machine/material/tool/pricing machineId={}", m.getId());
    }

    private void seedHobbyTools(ToolRepository tools, Long machineId) {
        // Typical hobby CNC timber kit
        saveTool(tools, machineId, "3.175mm 2F Upcut", "ENDMILL", 3.175, 2, 12, 1.5);
        saveTool(tools, machineId, "6mm 2F Upcut", "ENDMILL", 6.0, 2, 20, 2.5);
        saveTool(tools, machineId, "6mm 2F Downcut", "ENDMILL", 6.0, 2, 18, 2.5);
        saveTool(tools, machineId, "6mm Compression", "ENDMILL", 6.0, 2, 22, 3.0);
        saveTool(tools, machineId, "3mm Ballnose", "BALLNOSE", 3.0, 2, 10, 2.0);
        saveTool(tools, machineId, "6mm Ballnose", "BALLNOSE", 6.0, 2, 15, 2.5);
        saveTool(tools, machineId, "60° V-bit", "VBIT", 6.0, 1, 8, 1.5);
        saveTool(tools, machineId, "90° V-bit", "VBIT", 12.0, 1, 6, 1.5);
        saveTool(tools, machineId, "25mm Surfacing", "SURFACING", 25.0, 3, 3, 4.0);
        saveTool(tools, machineId, "3mm Drill", "DRILL", 3.0, 2, 25, 0.5);
        saveTool(tools, machineId, "5mm Drill", "DRILL", 5.0, 2, 30, 0.5);
        saveTool(tools, machineId, "8mm Drill", "DRILL", 8.0, 2, 35, 0.8);
    }

    private void saveTool(ToolRepository tools, Long machineId, String name, String type,
                          double dia, int flutes, double maxDepth, double wear) {
        // Avoid duplicates by name on this machine
        boolean exists = tools.findByMachineId(machineId).stream()
                .anyMatch(t -> name.equalsIgnoreCase(t.getName()));
        if (exists) return;
        Tool t = new Tool();
        t.setMachineId(machineId);
        t.setName(name);
        t.setType(type);
        t.setDiameterMm(dia);
        t.setFluteCount(flutes);
        t.setMaxDepthMm(maxDepth);
        t.setWearCharge(wear);
        tools.save(t);
    }

    private void saveRule(PricingRuleRepository repo, Long machineId, String key, String name, double value) {
        PricingRule r = new PricingRule();
        r.setMachineId(machineId);
        r.setRuleKey(key);
        r.setName(name);
        r.setValue(value);
        r.setDescription(name);
        repo.save(r);
    }
}
