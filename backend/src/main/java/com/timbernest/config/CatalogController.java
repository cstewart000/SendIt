package com.timbernest.config;

import com.timbernest.admin.*;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/catalog")
public class CatalogController {
    private final MachineRepository machines;
    private final ToolRepository tools;
    private final MaterialRepository materials;
    private final ProcessRepository processes;

    public CatalogController(MachineRepository machines, ToolRepository tools,
                             MaterialRepository materials, ProcessRepository processes) {
        this.machines = machines;
        this.tools = tools;
        this.materials = materials;
        this.processes = processes;
    }

    @GetMapping
    public Map<String, Object> catalog() {
        return Map.of(
                "machines", machines.findAll(),
                "tools", tools.findAll(),
                "materials", materials.findAll(),
                "processes", processes.findAll()
        );
    }

    @GetMapping("/machines")
    public List<Machine> machines() { return machines.findAll(); }

    @GetMapping("/materials")
    public List<Material> materials() { return materials.findAll(); }

    @GetMapping("/tools")
    public List<Tool> tools() { return tools.findAll(); }
}
