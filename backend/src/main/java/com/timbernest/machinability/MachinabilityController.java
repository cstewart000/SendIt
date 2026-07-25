package com.timbernest.machinability;

import com.timbernest.admin.*;
import com.timbernest.common.ApiException;
import com.timbernest.design.DesignAccess;
import com.timbernest.design.DesignVersion;
import com.timbernest.design.DesignVersionRepository;
import com.timbernest.geometry.JsonUtil;
import com.timbernest.geometry.model.GeoIssue;
import com.timbernest.geometry.model.GeometryModel;
import com.timbernest.user.AppUser;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

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

    public MachinabilityController(DesignAccess access, DesignVersionRepository versions,
                                   MachinabilityService machinability, DogBoneService dogBones,
                                   ToolRepository tools, MaterialRepository materials, JsonUtil json) {
        this.access = access; this.versions = versions; this.machinability = machinability;
        this.dogBones = dogBones; this.tools = tools; this.materials = materials; this.json = json;
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

    @PostMapping("/dogbones")
    public Map<String, Object> dogbones(@AuthenticationPrincipal AppUser user,
                                        @PathVariable Long id, @PathVariable Long vid,
                                        @RequestBody Map<String, Object> body) {
        DesignVersion v = access.ownedVersion(user, id, vid);
        GeometryModel model = access.loadOrParse(v);
        Long toolId = ((Number) body.get("toolId")).longValue();
        double scale = body.get("scale") == null ? 1.0 : ((Number) body.get("scale")).doubleValue();
        Tool tool = tools.findById(toolId)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "tool not found"));
        int n = dogBones.apply(model, tool, scale);
        v.setGeometryJson(json.toJson(model));
        v.setRepaired(true);
        versions.save(v);
        return Map.of("dogBonesAdded", n, "geometry", model);
    }
}
