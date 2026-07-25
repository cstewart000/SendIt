package com.timbernest.design;

import com.timbernest.common.ApiException;
import com.timbernest.geometry.DxfParser;
import com.timbernest.geometry.JsonUtil;
import com.timbernest.geometry.model.GeometryModel;
import com.timbernest.user.AppUser;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.Locale;

@Component
public class DesignAccess {
    private final DesignRepository designs;
    private final DesignVersionRepository versions;
    private final DxfParser dxfParser;
    private final JsonUtil json;

    public DesignAccess(DesignRepository designs, DesignVersionRepository versions,
                        DxfParser dxfParser, JsonUtil json) {
        this.designs = designs;
        this.versions = versions;
        this.dxfParser = dxfParser;
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
        if (v.getGeometryJson() != null && !v.getGeometryJson().isBlank()) {
            return json.toModel(v.getGeometryJson());
        }
        String name = v.getOriginalFilename().toLowerCase(Locale.ROOT);
        if (name.endsWith(".dwg")) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "DWG native parse not available in Phase 1 – please re-export as DXF");
        }
        GeometryModel model = dxfParser.parse(Path.of(v.getOriginalPath()));
        v.setGeometryJson(json.toJson(model));
        versions.save(v);
        return model;
    }
}
