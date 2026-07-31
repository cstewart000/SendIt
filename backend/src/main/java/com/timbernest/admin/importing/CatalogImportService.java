package com.timbernest.admin.importing;

import com.timbernest.admin.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

@Service
public class CatalogImportService {
    private static final Logger log = LoggerFactory.getLogger(CatalogImportService.class);
    private final MachineRepository machines;
    private final ToolRepository tools;
    private final LinuxCncImporter linux;
    private final Fusion360Importer fusion;

    public CatalogImportService(MachineRepository machines, ToolRepository tools,
                                LinuxCncImporter linux, Fusion360Importer fusion) {
        this.machines = machines; this.tools = tools; this.linux = linux; this.fusion = fusion;
    }

    public ImportResult importLinuxMachine(MultipartFile f) throws Exception {
        return saveMachine(linux.parseIni(read(f)), "linuxcnc");
    }

    public ImportResult importLinuxTools(Long machineId, MultipartFile f) throws Exception {
        return saveTools(machineId, linux.parseToolTable(read(f), machineId), "linuxcnc");
    }

    public ImportResult importFusionMachine(MultipartFile f) throws Exception {
        return saveMachine(fusion.parseMachine(read(f)), "fusion360");
    }

    public ImportResult importFusionTools(Long machineId, MultipartFile f) throws Exception {
        return saveTools(machineId, fusion.parseTools(read(f), machineId), "fusion360");
    }

    public ImportResult autoImport(Long machineId, MultipartFile file) throws Exception {
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        String body = read(file);
        if (name.endsWith(".ini") || body.contains("[TRAJ]") || body.contains("[AXIS_X]")) {
            return saveMachine(linux.parseIni(body), "linuxcnc");
        }
        if (name.endsWith(".tbl") || (name.contains("tool") && !body.trim().startsWith("{"))) {
            return saveTools(require(machineId), linux.parseToolTable(body, machineId), "linuxcnc");
        }
        if (body.trim().startsWith("{")) {
            if (body.contains("\"data\"") || body.contains("\"geometry\"")) {
                return saveTools(require(machineId), fusion.parseTools(body, machineId), "fusion360");
            }
            return saveMachine(fusion.parseMachine(body), "fusion360");
        }
        throw new IllegalArgumentException("Unrecognized file — use LinuxCNC .ini/.tbl or Fusion .json");
    }

    private ImportResult saveMachine(Machine m, String source) {
        machines.save(m);
        log.info("Imported {} machine id={}", source, m.getId());
        return ImportResult.of(source, "Imported machine " + m.getName(), m.getId(), 1, 0);
    }

    private ImportResult saveTools(Long machineId, List<Tool> list, String source) {
        require(machineId);
        tools.saveAll(list);
        log.info("Imported {} tools={} machine={}", source, list.size(), machineId);
        return ImportResult.of(source, "Imported " + list.size() + " tools", machineId, 0, list.size());
    }

    private Long require(Long id) {
        if (id == null || machines.findById(id).isEmpty()) {
            throw new IllegalArgumentException("Select a machine before importing tools");
        }
        return id;
    }

    private String read(MultipartFile file) throws Exception {
        return new String(file.getBytes(), StandardCharsets.UTF_8);
    }
}
