package com.timbernest.design;

import com.timbernest.admin.Tool;
import com.timbernest.admin.ToolRepository;
import com.timbernest.common.ApiException;
import com.timbernest.geometry.ContourJoiner;
import com.timbernest.geometry.GeometryAnalyser;
import com.timbernest.geometry.GeometryRepairer;
import com.timbernest.geometry.JsonUtil;
import com.timbernest.geometry.model.GeometryModel;
import com.timbernest.machinability.DogBoneService;
import com.timbernest.storage.FileStorageService;
import com.timbernest.user.AppUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
    private final DogBoneService dogBones;
    private final ToolRepository tools;

    public DesignService(DesignRepository designs, DesignVersionRepository versions,
                         DesignPartRepository designParts, RepairActionLogRepository repairLogs,
                         FileStorageService storage, GeometryAnalyser analyser,
                         GeometryRepairer repairer, JsonUtil json, DesignAccess access,
                         DesignPartSync partSync, DogBoneService dogBones, ToolRepository tools) {
        this.designs = designs; this.versions = versions; this.designParts = designParts;
        this.repairLogs = repairLogs; this.storage = storage; this.analyser = analyser;
        this.repairer = repairer; this.json = json; this.access = access; this.partSync = partSync;
        this.dogBones = dogBones; this.tools = tools;
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

    @Transactional
    public DesignDtos.VersionDto repair(AppUser user, Long designId, Long versionId,
                                       String action, boolean confirm) {
        if (!confirm) throw new ApiException(HttpStatus.BAD_REQUEST, "Repair must be confirmed");
        DesignVersion v = access.ownedVersion(user, designId, versionId);
        GeometryModel model = access.loadOrParse(v);
        ContourJoiner.joinAdaptive(model);
        String reason = repairer.apply(model, action);
        ContourJoiner.joinAdaptive(model);
        return persistRepaired(v, model, action, reason);
    }

    /**
     * Apply dog-bones to sharp internal corners, save geometryJson + design parts, re-analyse.
     * Snapshots current geometry so {@link #undoDogbones} can restore it.
     */
    @Transactional
    public Map<String, Object> applyDogbones(AppUser user, Long designId, Long versionId,
                                             Long toolId, double scale, boolean confirm) {
        if (!confirm) throw new ApiException(HttpStatus.BAD_REQUEST, "Dog-bones must be confirmed");
        if (toolId == null) throw new ApiException(HttpStatus.BAD_REQUEST, "toolId required");
        DesignVersion v = access.ownedVersion(user, designId, versionId);
        GeometryModel model = access.loadOrParse(v);
        Tool tool = tools.findById(toolId)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "tool not found"));

        // Snapshot for undo (always overwrite so undo reverses the latest apply)
        String snapshot = v.getGeometryJson();
        if (snapshot == null || snapshot.isBlank()) {
            snapshot = json.toJson(model);
        }
        v.setPreDogboneGeometryJson(snapshot);

        int before = dogBones.countCandidates(model);
        int pointsBefore = model.getContours().stream().mapToInt(c -> c.getPoints().size()).sum();
        DogBoneService.Result r = dogBones.apply(model, tool, scale);
        int pointsAfter = model.getContours().stream().mapToInt(c -> c.getPoints().size()).sum();

        String reason = String.format("Dog-bones at %d corner(s), radius %.2f mm (tool %s ×%.2f)",
                r.corners(), r.radiusMm(), tool.getName(), scale);
        DesignDtos.VersionDto version = persistRepaired(v, model, "DOGBONES", reason);

        // Verify round-trip from DB
        DesignVersion reloaded = versions.findById(v.getId()).orElseThrow();
        GeometryModel check = json.toModel(reloaded.getGeometryJson());
        int pointsStored = check.getContours().stream().mapToInt(c -> c.getPoints().size()).sum();
        log.info("Dog-bones version {} corners={} points {}→{} stored={}",
                v.getId(), r.corners(), pointsBefore, pointsAfter, pointsStored);
        if (r.corners() > 0 && pointsStored <= pointsBefore) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Dog-bones did not persist into geometryJson");
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("dogBonesAdded", r.corners());
        out.put("radiusMm", r.radiusMm());
        out.put("candidatesBefore", before);
        out.put("pointsBefore", pointsBefore);
        out.put("pointsAfter", pointsAfter);
        out.put("pointsStored", pointsStored);
        out.put("canUndo", true);
        out.put("message", reason);
        out.put("version", version);
        out.put("geometry", check);
        return out;
    }

    /**
     * Restore geometry from the snapshot taken before the last dog-bone apply.
     */
    @Transactional
    public Map<String, Object> undoDogbones(AppUser user, Long designId, Long versionId) {
        DesignVersion v = access.ownedVersion(user, designId, versionId);
        if (!v.hasPreDogboneSnapshot()) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "No dog-bone snapshot to undo — apply dog-bones first");
        }
        GeometryModel restored = json.toModel(v.getPreDogboneGeometryJson());
        v.setPreDogboneGeometryJson(null);
        String reason = "Undo dog-bones (restored pre-dogbone geometry)";
        DesignDtos.VersionDto version = persistRepaired(v, restored, "UNDO_DOGBONES", reason);

        DesignVersion reloaded = versions.findById(v.getId()).orElseThrow();
        GeometryModel check = json.toModel(reloaded.getGeometryJson());
        int points = check.getContours().stream().mapToInt(c -> c.getPoints().size()).sum();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("undone", true);
        out.put("canUndo", false);
        out.put("pointsStored", points);
        out.put("message", reason);
        out.put("version", version);
        out.put("geometry", check);
        return out;
    }

    public Map<String, Object> dogbonePreview(AppUser user, Long designId, Long versionId, Long toolId) {
        DesignVersion v = access.ownedVersion(user, designId, versionId);
        GeometryModel model = access.loadOrParse(v);
        int candidates = dogBones.countCandidates(model);
        double radius = tools.findById(toolId == null ? -1L : toolId)
                .map(t -> t.getDiameterMm() / 2.0).orElse(3.0);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("candidates", candidates);
        out.put("toolRadiusMm", radius);
        out.put("canUndo", v.hasPreDogboneSnapshot());
        out.put("message", candidates == 0
                ? "No sharp internal corners found"
                : candidates + " sharp internal corner(s) can receive dog-bones");
        return out;
    }

    private DesignDtos.VersionDto persistRepaired(DesignVersion v, GeometryModel model,
                                                  String action, String reason) {
        v.setGeometryJson(json.toJson(model));
        v.setIssuesJson(json.toJson(analyser.analyse(model)));
        v.setRepaired(true);
        v.setAnalysed(true);
        v.setRepairedPath(storage.writeText("repaired", "v" + v.getId() + ".json", v.getGeometryJson()));
        DesignVersion saved = versions.saveAndFlush(v);
        partSync.sync(saved.getId(), model);
        repairLogs.save(RepairActionLog.of(saved.getId(), action, reason));
        // Touch parent design so list views refresh
        designs.findById(saved.getDesignId()).ifPresent(d -> {
            d.touch();
            designs.save(d);
        });
        log.info("Persisted repaired geometry version {} action={} reason={}",
                saved.getId(), action, reason);
        return toVersion(saved);
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
