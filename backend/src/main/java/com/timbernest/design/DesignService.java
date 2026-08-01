package com.timbernest.design;

import com.timbernest.common.ApiException;
import com.timbernest.geometry.ContourJoiner;
import com.timbernest.geometry.GeometryAnalyser;
import com.timbernest.geometry.GeometryRepairer;
import com.timbernest.geometry.JsonUtil;
import com.timbernest.geometry.model.GeometryModel;
import com.timbernest.storage.FileStorageService;
import com.timbernest.user.AppUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
public class DesignService {
    private static final Logger log = LoggerFactory.getLogger(DesignService.class);
    private final DesignRepository designs;
    private final DesignVersionRepository versions;
    private final DesignPartRepository designParts;
    private final RepairActionLogRepository repairLogs;
    private final FileStorageService storage;
    private final GeometryAnalyser analyser;
    private final GeometryRepairer repairer;
    private final JsonUtil json;
    private final DesignAccess access;
    private final DesignPartSync partSync;

    public DesignService(DesignRepository designs, DesignVersionRepository versions,
                         DesignPartRepository designParts, RepairActionLogRepository repairLogs,
                         FileStorageService storage, GeometryAnalyser analyser,
                         GeometryRepairer repairer, JsonUtil json, DesignAccess access,
                         DesignPartSync partSync) {
        this.designs = designs; this.versions = versions; this.designParts = designParts;
        this.repairLogs = repairLogs; this.storage = storage; this.analyser = analyser;
        this.repairer = repairer; this.json = json; this.access = access; this.partSync = partSync;
    }

    public List<DesignDtos.DesignSummary> list(AppUser user) {
        return designs.findByOwnerIdOrderByUpdatedAtDesc(user.getId()).stream()
                .map(d -> {
                    var top = versions.findTopByDesignIdOrderByVersionNumberDesc(d.getId());
                    int v = top.map(DesignVersion::getVersionNumber).orElse(0);
                    int parts = top.map(tv -> (int) designParts.countByDesignVersionId(tv.getId())).orElse(0);
                    return new DesignDtos.DesignSummary(d.getId(), d.getName(), d.getUpdatedAt(), v, parts);
                }).toList();
    }

    public DesignDtos.DesignDetail get(AppUser user, Long id) {
        Design d = access.owned(user, id);
        List<DesignDtos.VersionDto> vs = versions.findByDesignIdOrderByVersionNumberDesc(id)
                .stream().map(this::toVersion).toList();
        return new DesignDtos.DesignDetail(d.getId(), d.getName(), vs);
    }

    public DesignDtos.DesignDetail upload(AppUser user, String name, MultipartFile file) {
        validateFile(file);
        Design d = new Design();
        d.setOwnerId(user.getId());
        d.setName(name == null || name.isBlank() ? file.getOriginalFilename() : name);
        designs.save(d);
        createVersion(d, file, 1);
        log.info("Created design id={} for user={}", d.getId(), user.getId());
        return get(user, d.getId());
    }

    public DesignDtos.VersionDto upRev(AppUser user, Long designId, MultipartFile file) {
        Design d = access.owned(user, designId);
        validateFile(file);
        int next = versions.findTopByDesignIdOrderByVersionNumberDesc(designId)
                .map(v -> v.getVersionNumber() + 1).orElse(1);
        DesignVersion v = createVersion(d, file, next);
        d.touch();
        designs.save(d);
        return toVersion(v);
    }

    public DesignDtos.VersionDto analyse(AppUser user, Long designId, Long versionId) {
        DesignVersion v = access.ownedVersion(user, designId, versionId);
        GeometryModel model = access.loadOrParse(v);
        // Re-join on analyse so cached LINE/ARC chains (pre-fix uploads) seal into profiles
        ContourJoiner.joinAdaptive(model);
        v.setGeometryJson(json.toJson(model));
        v.setIssuesJson(json.toJson(analyser.analyse(model)));
        v.setAnalysed(true);
        versions.save(v);
        partSync.sync(v.getId(), model);
        log.info("Analysed designVersionId={}", v.getId());
        return toVersion(v);
    }

    public DesignDtos.VersionDto repair(AppUser user, Long designId, Long versionId,
                                       String action, boolean confirm) {
        if (!confirm) throw new ApiException(HttpStatus.BAD_REQUEST, "Repair must be confirmed");
        DesignVersion v = access.ownedVersion(user, designId, versionId);
        GeometryModel model = access.loadOrParse(v);
        ContourJoiner.joinAdaptive(model);
        String reason = repairer.apply(model, action);
        ContourJoiner.joinAdaptive(model);
        v.setGeometryJson(json.toJson(model));
        v.setIssuesJson(json.toJson(analyser.analyse(model)));
        v.setRepaired(true);
        v.setRepairedPath(storage.writeText("repaired", "v" + v.getId() + ".json", v.getGeometryJson()));
        versions.save(v);
        partSync.sync(v.getId(), model);
        repairLogs.save(RepairActionLog.of(v.getId(), action, reason));
        log.info("Repair {} on version {}: {}", action, v.getId(), reason);
        return toVersion(v);
    }

    public List<DesignDtos.PartDto> listParts(AppUser user, Long designId, Long versionId) {
        access.ownedVersion(user, designId, versionId);
        return designParts.findByDesignVersionIdOrderByPartIndexAsc(versionId).stream()
                .map(this::toPart).toList();
    }

    private DesignVersion createVersion(Design d, MultipartFile file, int num) {
        DesignVersion v = new DesignVersion();
        v.setDesignId(d.getId());
        v.setVersionNumber(num);
        v.setOriginalFilename(file.getOriginalFilename());
        v.setOriginalPath(storage.storeOriginal(file));
        return versions.save(v);
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "File required");
        }
        String n = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        if (!(n.endsWith(".dxf") || n.endsWith(".dwg"))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Only DXF/DWG accepted");
        }
    }

    public DesignDtos.VersionDto toVersion(DesignVersion v) {
        GeometryModel g = v.getGeometryJson() == null ? null : json.toModel(v.getGeometryJson());
        int count = (int) designParts.countByDesignVersionId(v.getId());
        return new DesignDtos.VersionDto(v.getId(), v.getVersionNumber(), v.getOriginalFilename(),
                v.isAnalysed(), v.isRepaired(), v.isWarningsAcknowledged(), v.getCreatedAt(),
                g, json.toIssues(v.getIssuesJson()), count);
    }

    DesignDtos.PartDto toPart(DesignPart p) {
        return new DesignDtos.PartDto(p.getId(), p.getPartIndex(), p.getLabel(), p.getContourId(),
                p.getWidthMm(), p.getHeightMm(), json.toModel(p.getGeometryJson()));
    }
}
