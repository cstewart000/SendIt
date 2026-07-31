package com.timbernest.admin;

import com.timbernest.user.AppUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminCatalogController {
    private static final Logger log = LoggerFactory.getLogger(AdminCatalogController.class);
    private final MachineRepository machines;
    private final ToolRepository tools;
    private final MaterialRepository materials;
    private final ProcessRepository processes;
    private final PricingRuleRepository pricing;
    private final AuditLogRepository audit;

    public AdminCatalogController(MachineRepository machines, ToolRepository tools,
                                  MaterialRepository materials, ProcessRepository processes,
                                  PricingRuleRepository pricing, AuditLogRepository audit) {
        this.machines = machines; this.tools = tools; this.materials = materials;
        this.processes = processes; this.pricing = pricing; this.audit = audit;
    }

    @GetMapping("/machines")
    public List<Machine> machines() { return machines.findAll(); }

    @PostMapping("/machines")
    public Machine saveMachine(@AuthenticationPrincipal AppUser admin, @RequestBody Machine m) {
        Machine saved = machines.save(m);
        audit(admin, "UPSERT_MACHINE", "id=" + saved.getId());
        return saved;
    }

    @GetMapping("/tools")
    public List<Tool> tools() { return tools.findAll(); }

    @PostMapping("/tools")
    public Tool saveTool(@AuthenticationPrincipal AppUser admin, @RequestBody Tool t) {
        if (t.getMachineId() == null) throw new IllegalArgumentException("Tool requires machineId");
        Tool saved = tools.save(t);
        audit(admin, "UPSERT_TOOL", "machine=" + saved.getMachineId() + " id=" + saved.getId());
        return saved;
    }

    @GetMapping("/materials")
    public List<Material> materials() { return materials.findAll(); }

    @PostMapping("/materials")
    public Material saveMaterial(@AuthenticationPrincipal AppUser admin, @RequestBody Material m) {
        Material saved = materials.save(m);
        audit(admin, "UPSERT_MATERIAL", "id=" + saved.getId());
        return saved;
    }

    @GetMapping("/processes")
    public List<ProcessDef> processes() { return processes.findAll(); }

    @PostMapping("/processes")
    public ProcessDef saveProcess(@AuthenticationPrincipal AppUser admin, @RequestBody ProcessDef p) {
        if (p.getMachineId() == null) throw new IllegalArgumentException("Process requires machineId");
        ProcessDef saved = processes.save(p);
        audit(admin, "UPSERT_PROCESS", "machine=" + saved.getMachineId() + " id=" + saved.getId());
        return saved;
    }

    @GetMapping("/pricing")
    public List<PricingRule> pricing(@RequestParam(required = false) Long machineId) {
        return machineId == null ? pricing.findAll() : pricing.findByMachineId(machineId);
    }

    @PostMapping("/pricing")
    public PricingRule savePricing(@AuthenticationPrincipal AppUser admin, @RequestBody PricingRule r) {
        if (r.getMachineId() == null) {
            throw new IllegalArgumentException("Pricing rule requires machineId");
        }
        PricingRule saved = pricing.save(r);
        audit(admin, "UPSERT_PRICING", "machine=" + saved.getMachineId()
                + " key=" + saved.getRuleKey() + " value=" + saved.getValue());
        return saved;
    }

    @GetMapping("/machines/{id}/tools")
    public List<Tool> machineTools(@PathVariable Long id) { return tools.findByMachineId(id); }

    @GetMapping("/machines/{id}/processes")
    public List<ProcessDef> machineProcesses(@PathVariable Long id) {
        return processes.findByMachineId(id);
    }

    @GetMapping("/machines/{id}/pricing")
    public List<PricingRule> machinePricing(@PathVariable Long id) {
        return pricing.findByMachineId(id);
    }

    @GetMapping("/audit")
    public List<AuditLog> auditLog() { return audit.findAll(); }

    private void audit(AppUser admin, String action, String details) {
        audit.save(AuditLog.of(admin.getId(), action, details));
        log.info("Admin audit {} {}", action, details);
    }
}
