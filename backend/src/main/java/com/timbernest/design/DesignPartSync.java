package com.timbernest.design;

import com.timbernest.geometry.JsonUtil;
import com.timbernest.geometry.PartExtractor;
import com.timbernest.geometry.model.GeometryModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Component
public class DesignPartSync {
    private static final Logger log = LoggerFactory.getLogger(DesignPartSync.class);
    private final DesignPartRepository parts;
    private final PartExtractor extractor;
    private final JsonUtil json;

    public DesignPartSync(DesignPartRepository parts, PartExtractor extractor, JsonUtil json) {
        this.parts = parts;
        this.extractor = extractor;
        this.json = json;
    }

    @Transactional
    public List<DesignPart> sync(Long versionId, GeometryModel model) {
        parts.deleteByDesignVersionId(versionId);
        List<DesignPart> saved = new ArrayList<>();
        int i = 0;
        for (PartExtractor.ExtractedPart ep : extractor.extract(model)) {
            DesignPart p = new DesignPart();
            p.setDesignVersionId(versionId);
            p.setPartIndex(++i);
            p.setLabel(ep.label());
            p.setContourId(ep.contourId());
            p.setGeometryJson(json.toJson(ep.geometry()));
            p.setWidthMm(ep.widthMm());
            p.setHeightMm(ep.heightMm());
            saved.add(parts.save(p));
        }
        log.info("Synced {} design parts for version {}", saved.size(), versionId);
        return saved;
    }
}
