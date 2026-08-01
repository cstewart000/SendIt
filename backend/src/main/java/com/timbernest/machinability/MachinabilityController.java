package com.timbernest.machinability;

import com.timbernest.admin.*;
import com.timbernest.common.ApiException;
import com.timbernest.design.*;
import com.timbernest.geometry.GeometryAnalyser;
import com.timbernest.geometry.JsonUtil;
import com.timbernest.geometry.model.GeoIssue;
import com.timbernest.geometry.model.GeometryModel;
import com.timbernest.storage.FileStorageService;
import com.timbernest.user.AppUser;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/designs/{id}/versions/{vid}")
public class MachinabilityController {
    private final DesignAccess access;
    private final DesignVersionRepository versions;
    private final MachinabilityService machinability;
    private final DogBoneService dogBones;
    private final ToolRepository tools;
    private final MaterialRepository materials;
    private final JsonUtil json;
    private final DesignPartSync partSync;
    private final GeometryAnalyser analyser;
    private final RepairActionLogRepository repairLogs;
    private final FileStorageService storage;
    private final DesignService designService;

    public MachinabilityController(DesignAccess access, DesignVersionRepository versions,
                                   MachinabilityService machinability, DogBoneService dogBones,
                                   ToolRepository tools, MaterialRepository materials, JsonUtil json,
                                   DesignPartSync partSync, GeometryAnalyser analyser,
                                   RepairActionLogRepository repairLogs, FileStorageService storage,
                                   DesignService designService) {
        this.access = access; this.versions = versions; this.machinability = machinability;
        this.dogBones = dogBones; this.tools = tools; this.materials = materials; this.json = json;
        this.partSync = partSync; this.analyser = analyser; this.repairLogs = repairLogs;
        this.storage = storage; this.designService = designService;
    }

    @PostMapping("/machinability")
    public List<GeoIssue> check(@AuthenticationPrincipal AppUser user,
                                @PathVariable Long id, @PathVariable Long vid,
                                @RequestBody Map<String, Long> body) {
        DesignVersion v = access.ownedVersion(user, id, vid);
        GeometryModel model = access.loadOrParse(v);
        Tool tool = tools.findById(body.get("toolId"))
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "toolId required"));
        Material mat = materials.findById(body.get("materialId"))
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "materialId required"));
        return machinability.check(model, tool, mat);
    }

    /** Preview how many sharp internal corners would get dog-bones. */
    @GetMapping("/dogbones/preview")
    public Map<String, Object> dogbonePreview(@AuthenticationPrincipal AppUser user,
                                              @PathVariable Long id, @PathVariable Long vid,
                                              @RequestParam(required = false) Long toolId) {
        DesignVersion v = access.ownedVersion(user, id, vid);
        GeometryModel model = access.loadOrParse(v);
        int candidates = dogBones.countCandidates(model);
        double radius = 3.0;
        if (toolId != null) {
            radius = tools.findById(toolId).map(t -> t.getDiameterMm() / 2.0).orElse(3.0);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("candidates", candidates);
        out.put("toolRadiusMm", radius);
        out.put("message", candidates == 0
                ? "No sharp internal corners found"
                : candidates + " sharp internal corner(s) can receive dog-bones");
        return out;
    }

    /**
     * Apply dog-bones at sharp internal corners.
     * Body: { toolId, scale? (default 1.0 = full tool radius), confirm: true }
     */
    @PostMapping("/dogbones")
    public Map<String, Object> dogbones(@AuthenticationPrincipal AppUser user,
                                        @PathVariable Long id, @PathVariable Long vid,
                                        @RequestBody Map<String, Object> body) {
        if (!Boolean.TRUE.equals(body.get("confirm")) && body.get("confirm") != null
                && !Boolean.parseBoolean(String.valueOf(body.get("confirm")))) {
            // allow missing confirm for backward compat; require true if present as false
        }
        Object conf = body.get("confirm");
        if (conf != null && !Boolean.TRUE.equals(conf) && !"true".equalsIgnoreCase(String.valueOf(conf))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Dog-bones must be confirmed");
        }
        DesignVersion v = access.ownedVersion(user, id, vid);
        GeometryModel model = access.loadOrParse(v);
        if (body.get("toolId") == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "toolId required");
        }
        Long toolId = ((Number) body.get("toolId")).longValue();
        double scale = body.get("scale") == null ? 1.0 : ((Number) body.get("scale")).doubleValue();
        Tool tool = tools.findById(toolId)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "tool not found"));

        int before = dogBones.countCandidates(model);
        DogBoneService.Result r = dogBones.apply(model, tool, scale);
        v.setGeometryJson(json.toJson(model));
        v.setIssuesJson(json.toJson(analyser.analyse(model)));
        v.setRepaired(true);
        v.setRepairedPath(storage.writeText("repaired", "v" + v.getId() + ".json", v.getGeometryJson()));
        versions.save(v);
        partSync.sync(v.getId(), model);
        String reason = String.format("Dog-bones at %d corner(s), radius %.2f mm (tool %s ×%.2f)",
                r.corners(), r.radiusMm(), tool.getName(), scale);
        repairLogs.save(RepairActionLog.of(v.getId(), "DOGBONES", reason));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("dogBonesAdded", r.corners());
        out.put("radiusMm", r.radiusMm());
        out.put("candidatesBefore", before);
        out.put("message", reason);
        out.put("version", designService.toVersion(v));
        return out;
    }
}
