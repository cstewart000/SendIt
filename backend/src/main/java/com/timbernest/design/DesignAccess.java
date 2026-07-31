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

    public GeometryModel loadOrParse(DesignVersion v) {
        String name = v.getOriginalFilename() == null ? "" : v.getOriginalFilename().toLowerCase(Locale.ROOT);
        boolean dwg = name.endsWith(".dwg");
        // Always re-parse DWG from disk — binary parse quality improves with cleaner filters.
        if (!dwg && v.getGeometryJson() != null && !v.getGeometryJson().isBlank()) {
            return json.toModel(v.getGeometryJson());
        }
        Path path = Path.of(v.getOriginalPath());
        log.info("Parsing design version {} file={}", v.getId(), name);
        GeometryModel model = dwg ? dwgParser.parse(path) : dxfParser.parse(path);
        v.setGeometryJson(json.toJson(model));
        versions.save(v);
        return model;
    }
}
