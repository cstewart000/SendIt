package com.timbernest.design;

import com.timbernest.user.AppUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/designs")
public class DesignController {
    private final DesignService designs;
    private final DesignAccess access;
    private final RepairActionLogRepository repairLogs;
    private final DesignVersionRepository versionRepo;

    public DesignController(DesignService designs, DesignAccess access,
                            RepairActionLogRepository repairLogs,
                            DesignVersionRepository versionRepo) {
        this.designs = designs;
        this.access = access;
        this.repairLogs = repairLogs;
        this.versionRepo = versionRepo;
    }

    @GetMapping
    public List<DesignDtos.DesignSummary> list(@AuthenticationPrincipal AppUser user) {
        return designs.list(user);
    }

    @GetMapping("/{id}")
    public DesignDtos.DesignDetail get(@AuthenticationPrincipal AppUser user, @PathVariable Long id) {
        return designs.get(user, id);
    }

    @PostMapping(consumes = "multipart/form-data")
    public DesignDtos.DesignDetail upload(@AuthenticationPrincipal AppUser user,
                                          @RequestParam(required = false) String name,
                                          @RequestParam("file") MultipartFile file) {
        return designs.upload(user, name, file);
    }

    @PostMapping(path = "/{id}/versions", consumes = "multipart/form-data")
    public DesignDtos.VersionDto upRev(@AuthenticationPrincipal AppUser user, @PathVariable Long id,
                                       @RequestParam("file") MultipartFile file) {
        return designs.upRev(user, id, file);
    }

    @PostMapping("/{id}/versions/{vid}/analyse")
    public DesignDtos.VersionDto analyse(@AuthenticationPrincipal AppUser user,
                                         @PathVariable Long id, @PathVariable Long vid) {
        return designs.analyse(user, id, vid);
    }

    @GetMapping("/{id}/versions/{vid}/issues")
    public Object issues(@AuthenticationPrincipal AppUser user,
                         @PathVariable Long id, @PathVariable Long vid) {
        return designs.analyse(user, id, vid).issues();
    }

    @PostMapping("/{id}/versions/{vid}/repairs/{action}")
    public DesignDtos.VersionDto repair(@AuthenticationPrincipal AppUser user,
                                        @PathVariable Long id, @PathVariable Long vid,
                                        @PathVariable String action,
                                        @RequestBody DesignDtos.RepairRequest body) {
        return designs.repair(user, id, vid, action, body.confirm());
    }

    @GetMapping("/{id}/versions/{vid}/repair-log")
    public List<RepairActionLog> logs(@AuthenticationPrincipal AppUser user,
                                      @PathVariable Long id, @PathVariable Long vid) {
        access.ownedVersion(user, id, vid);
        return repairLogs.findByDesignVersionIdOrderByCreatedAtAsc(vid);
    }

    @PostMapping("/{id}/versions/{vid}/acknowledge-warnings")
    public Map<String, Object> ack(@AuthenticationPrincipal AppUser user,
                                   @PathVariable Long id, @PathVariable Long vid,
                                   @RequestBody DesignDtos.AckRequest body) {
        var v = access.ownedVersion(user, id, vid);
        if (!body.acknowledge()) {
            throw new com.timbernest.common.ApiException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "Must acknowledge");
        }
        v.setWarningsAcknowledged(true);
        this.versionRepo.save(v);
        return Map.of("warningsAcknowledged", true, "versionId", vid);
    }
}
