package com.timbernest.admin.importing;

import com.timbernest.admin.AuditLog;
import com.timbernest.admin.AuditLogRepository;
import com.timbernest.user.AppUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/admin/import")
public class CatalogImportController {
    private static final Logger log = LoggerFactory.getLogger(CatalogImportController.class);
    private final CatalogImportService imports;
    private final AuditLogRepository audit;

    public CatalogImportController(CatalogImportService imports, AuditLogRepository audit) {
        this.imports = imports;
        this.audit = audit;
    }

    @PostMapping("/linuxcnc/machine")
    public ImportResult linuxMachine(@AuthenticationPrincipal AppUser a, @RequestParam MultipartFile file) throws Exception {
        return done(a, imports.importLinuxMachine(file));
    }

    @PostMapping("/linuxcnc/tools")
    public ImportResult linuxTools(@AuthenticationPrincipal AppUser a, @RequestParam Long machineId,
                                   @RequestParam MultipartFile file) throws Exception {
        return done(a, imports.importLinuxTools(machineId, file));
    }

    @PostMapping("/fusion360/machine")
    public ImportResult fusionMachine(@AuthenticationPrincipal AppUser a, @RequestParam MultipartFile file) throws Exception {
        return done(a, imports.importFusionMachine(file));
    }

    @PostMapping("/fusion360/tools")
    public ImportResult fusionTools(@AuthenticationPrincipal AppUser a, @RequestParam Long machineId,
                                    @RequestParam MultipartFile file) throws Exception {
        return done(a, imports.importFusionTools(machineId, file));
    }

    @PostMapping
    public ImportResult auto(@AuthenticationPrincipal AppUser a, @RequestParam(required = false) Long machineId,
                             @RequestParam MultipartFile file) throws Exception {
        return done(a, imports.autoImport(machineId, file));
    }

    private ImportResult done(AppUser admin, ImportResult r) {
        audit.save(AuditLog.of(admin.getId(), "IMPORT_" + r.source().toUpperCase(),
                r.message() + " machineId=" + r.machineId()));
        log.info("Import {} machines={} tools={}", r.source(), r.machinesCreated(), r.toolsCreated());
        return r;
    }
}
