package com.timbernest.design;

import com.timbernest.common.ApiException;
import com.timbernest.geometry.DwgParser;
import com.timbernest.geometry.DxfParser;
import com.timbernest.geometry.JsonUtil;
import com.timbernest.geometry.model.GeometryModel;
import com.timbernest.user.AppUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.Locale;

@Component
public class DesignAccess {
    private static final Logger log = LoggerFactory.getLogger(DesignAccess.class);
    private final DesignRepository designs;
    private final DesignVersionRepository versions;
    private final DxfParser dxfParser;
    private final DwgParser dwgParser;
    private final JsonUtil json;

    public DesignAccess(DesignRepository designs, DesignVersionRepository versions,
                        DxfParser dxfParser, DwgParser dwgParser, JsonUtil json) {
        this.designs = designs;
        this.versions = versions;
        this.dxfParser = dxfParser;
        this.dwgParser = dwgParser;
        this.json = json;
    }

    public Design owned(AppUser user, Long id) {
        Design d = designs.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Design not found"));
        if (!d.getOwnerId().equals(user.getId()) && !"ADMIN".equals(user.getRole())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Not your design");
        }
        return d;
    }

    public DesignVersion ownedVersion(AppUser user, Long designId, Long versionId) {
        owned(user, designId);
        return versions.findByIdAndDesignId(versionId, designId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Version not found"));
    }

    /**
     * Load geometry for a version.
     * Prefer persisted geometryJson (repairs/dog-bones live here). Only parse the original
     * file when no geometry has been stored yet — never wipe repaired geometry by re-parsing.
     */
    public GeometryModel loadOrParse(DesignVersion v) {
        if (v.getGeometryJson() != null && !v.getGeometryJson().isBlank()) {
            GeometryModel model = json.toModel(v.getGeometryJson());
            log.debug("Loaded geometryJson for version {} ({} contours)",
                    v.getId(), model.getContours().size());
            return model;
        }
        return parseOriginalAndStore(v);
    }

    /** Force re-parse of original file (discards in-memory repairs not yet saved). */
    public GeometryModel reparseOriginal(DesignVersion v) {
        return parseOriginalAndStore(v);
    }

    private GeometryModel parseOriginalAndStore(DesignVersion v) {
        String name = v.getOriginalFilename() == null ? "" : v.getOriginalFilename().toLowerCase(Locale.ROOT);
        boolean dwg = name.endsWith(".dwg");
        Path path = Path.of(v.getOriginalPath());
        log.info("Parsing design version {} file={} (no geometryJson yet)", v.getId(), name);
        GeometryModel model = dwg ? dwgParser.parse(path) : dxfParser.parse(path);
        v.setGeometryJson(json.toJson(model));
        versions.save(v);
        return model;
    }

    /** Persist model into version geometryJson and flush. */
    public DesignVersion saveGeometry(DesignVersion v, GeometryModel model) {
        String geo = json.toJson(model);
        v.setGeometryJson(geo);
        DesignVersion saved = versions.saveAndFlush(v);
        log.info("Saved geometryJson for version {} chars={} contours={}",
                saved.getId(), geo.length(), model.getContours().size());
        return saved;
    }
}
