package com.timbernest.machinability;

import com.timbernest.admin.*;
import com.timbernest.common.ApiException;
import com.timbernest.design.DesignAccess;
import com.timbernest.design.DesignService;
import com.timbernest.design.DesignVersion;
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
    private final DesignService designService;
    private final MachinabilityService machinability;
    private final ToolRepository tools;
    private final MaterialRepository materials;

    public MachinabilityController(DesignAccess access, DesignService designService,
                                   MachinabilityService machinability,
                                   ToolRepository tools, MaterialRepository materials) {
        this.access = access;
        this.designService = designService;
        this.machinability = machinability;
        this.tools = tools;
        this.materials = materials;
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

    @GetMapping("/dogbones/preview")
    public Map<String, Object> dogbonePreview(@AuthenticationPrincipal AppUser user,
                                              @PathVariable Long id, @PathVariable Long vid,
                                              @RequestParam(required = false) Long toolId) {
        return designService.dogbonePreview(user, id, vid, toolId);
    }

    /**
     * Apply dog-bones; persists geometryJson + design parts and returns updated version + geometry.
     * Body: { toolId, scale?, confirm: true }
     */
    @PostMapping("/dogbones")
    public Map<String, Object> dogbones(@AuthenticationPrincipal AppUser user,
                                        @PathVariable Long id, @PathVariable Long vid,
                                        @RequestBody Map<String, Object> body) {
        Object conf = body.get("confirm");
        boolean confirm = conf == null || Boolean.TRUE.equals(conf)
                || "true".equalsIgnoreCase(String.valueOf(conf));
        if (body.get("toolId") == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "toolId required");
        }
        Long toolId = ((Number) body.get("toolId")).longValue();
        double scale = body.get("scale") == null ? 1.0 : ((Number) body.get("scale")).doubleValue();
        return designService.applyDogbones(user, id, vid, toolId, scale, confirm);
    }
}
