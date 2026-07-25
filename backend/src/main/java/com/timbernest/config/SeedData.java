package com.timbernest.config;

import com.timbernest.admin.*;
import com.timbernest.user.AppUser;
import com.timbernest.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class SeedData {
    private static final Logger log = LoggerFactory.getLogger(SeedData.class);

    @Bean
    CommandLineRunner seed(UserRepository users, MachineRepository machines, ToolRepository tools,
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
        };
    }

    private void seedShop(MachineRepository machines, ToolRepository tools, MaterialRepository materials,
                          ProcessRepository processes, PricingRuleRepository pricing) {
        Machine m = new Machine();
        m.setName("Shop Router 2440");
        machines.save(m);
        Tool t = new Tool();
        t.setMachineId(m.getId());
        t.setName("6mm Upcut Endmill");
        t.setDiameterMm(6);
        tools.save(t);
        Material mat = new Material();
        mat.setName("Plywood 18mm");
        materials.save(mat);
        ProcessDef p = new ProcessDef();
        p.setName("CNC Profile Plywood");
        p.setMachineId(m.getId());
        p.setMaterialId(mat.getId());
        processes.save(p);
        saveRule(pricing, "SETUP_FEE", "Setup fee", 25);
        saveRule(pricing, "MIN_ORDER", "Minimum order", 40);
        saveRule(pricing, "MATERIAL_MARKUP", "Material markup multiplier", 1.0);
        log.info("Seeded machine/material/tool/pricing");
    }

    private void saveRule(PricingRuleRepository repo, String key, String name, double value) {
        PricingRule r = new PricingRule();
        r.setRuleKey(key);
        r.setName(name);
        r.setValue(value);
        r.setDescription(name);
        repo.save(r);
    }
}
