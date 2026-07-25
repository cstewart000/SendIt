package com.timbernest.machinability;

import com.timbernest.admin.Material;
import com.timbernest.admin.Tool;
import com.timbernest.geometry.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class MachinabilityService {
    private static final Logger log = LoggerFactory.getLogger(MachinabilityService.class);

    public List<GeoIssue> check(GeometryModel model, Tool tool, Material material) {
        List<GeoIssue> issues = new ArrayList<>();
        double radius = tool.getDiameterMm() / 2.0;
        int n = 0;
        for (Contour c : model.getContours()) {
            if (!c.isClosed() || c.getPoints().size() < 3) continue;
            List<Vec2> pts = c.getPoints();
            for (int i = 0; i < pts.size(); i++) {
                Vec2 prev = pts.get((i - 1 + pts.size()) % pts.size());
                Vec2 cur = pts.get(i);
                Vec2 next = pts.get((i + 1) % pts.size());
                double turn = interiorAngle(prev, cur, next);
                if (turn < Math.PI - 0.15 && isInternalCorner(prev, cur, next, c)) {
                    issues.add(new GeoIssue("M" + (++n), "SHARP_CORNER", "warning",
                            String.format("Internal corner sharper than tool radius (%.1fmm)", radius),
                            c.getId(), List.of(prev, cur, next), true));
                }
            }
            double[] b = c.bbox();
            double minDim = Math.min(b[2] - b[0], b[3] - b[1]);
            if (minDim > 0 && minDim < material.getMinFeatureMm()) {
                issues.add(new GeoIssue("M" + (++n), "MIN_FEATURE", "error",
                        "Feature smaller than minimum tool/feature size",
                        c.getId(), List.copyOf(pts), false));
            }
            if (minDim > 0 && minDim < material.getThinWallThresholdMm()) {
                issues.add(new GeoIssue("M" + (++n), "THIN_WALL", "warning",
                        "Possible thin wall below threshold",
                        c.getId(), List.copyOf(pts), false));
            }
        }
        log.info("Machinability issues={}", issues.size());
        return issues;
    }

    private double interiorAngle(Vec2 a, Vec2 b, Vec2 c) {
        double ax = a.x() - b.x(), ay = a.y() - b.y();
        double cx = c.x() - b.x(), cy = c.y() - b.y();
        double dot = ax * cx + ay * cy;
        double denom = Math.hypot(ax, ay) * Math.hypot(cx, cy);
        if (denom < 1e-9) return Math.PI;
        return Math.acos(Math.max(-1, Math.min(1, dot / denom)));
    }

    private boolean isInternalCorner(Vec2 a, Vec2 b, Vec2 c, Contour contour) {
        double cross = (b.x() - a.x()) * (c.y() - b.y()) - (b.y() - a.y()) * (c.x() - b.x());
        return cross < 0;
    }
}
