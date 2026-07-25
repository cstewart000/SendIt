package com.timbernest.geometry;

import com.timbernest.geometry.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class GeometryAnalyser {
    private static final Logger log = LoggerFactory.getLogger(GeometryAnalyser.class);
    private static final double EPS = 0.05;

    public List<GeoIssue> analyse(GeometryModel model) {
        List<GeoIssue> issues = new ArrayList<>();
        int n = 0;
        for (Contour c : model.getContours()) {
            if (c.getPoints().size() < 2) {
                issues.add(issue(++n, "ZERO_LENGTH", "error", "Degenerate contour", c, true));
                continue;
            }
            if (c.pathLength() < EPS) {
                issues.add(issue(++n, "ZERO_LENGTH", "error", "Zero-length entity", c, true));
            }
            if (!c.isClosed() && c.getPoints().size() > 2) {
                Vec2 a = c.getPoints().get(0), b = c.getPoints().get(c.getPoints().size() - 1);
                if (a.dist(b) > EPS) {
                    issues.add(issue(++n, "OPEN_CONTOUR", "warning",
                            "Open contour – can close if endpoints are near", c, true));
                } else {
                    c.setClosed(true);
                }
            }
            if (tinySegments(c)) {
                issues.add(issue(++n, "TINY_FEATURE", "warning", "Extremely small segments", c, true));
            }
            if (selfIntersects(c)) {
                issues.add(issue(++n, "SELF_INTERSECTION", "error", "Self-intersecting polyline", c, false));
            }
            if (isTextLikeLayer(c.getLayer())) {
                issues.add(issue(++n, "NON_GEOMETRY", "info",
                        "Layer looks like text/dimension: " + c.getLayer(), c, true));
            }
        }
        issues.addAll(findDuplicates(model, n));
        double[] b = model.bbox();
        if (model.width() > 3000 || model.height() > 2000) {
            issues.add(new GeoIssue("I-SCALE", "SCALE", "warning",
                    "Bounds unusually large – check units/scale", null,
                    List.of(new Vec2(b[0], b[1]), new Vec2(b[2], b[3])), false));
        }
        log.info("Analysis found {} issues", issues.size());
        return issues;
    }

    private List<GeoIssue> findDuplicates(GeometryModel model, int start) {
        List<GeoIssue> out = new ArrayList<>();
        List<Contour> cs = model.getContours();
        for (int i = 0; i < cs.size(); i++) {
            for (int j = i + 1; j < cs.size(); j++) {
                if (sameShape(cs.get(i), cs.get(j))) {
                    out.add(issue(++start, "DUPLICATE", "warning", "Duplicate overlapping entity",
                            cs.get(j), true));
                }
            }
        }
        return out;
    }

    private boolean sameShape(Contour a, Contour b) {
        if (a.getPoints().size() != b.getPoints().size()) return false;
        for (int i = 0; i < a.getPoints().size(); i++) {
            if (a.getPoints().get(i).dist(b.getPoints().get(i)) > EPS) return false;
        }
        return true;
    }

    private boolean tinySegments(Contour c) {
        for (int i = 1; i < c.getPoints().size(); i++) {
            if (c.getPoints().get(i - 1).dist(c.getPoints().get(i)) < 0.2) return true;
        }
        return false;
    }

    private boolean selfIntersects(Contour c) {
        List<Vec2> p = c.getPoints();
        int n = p.size();
        if (n < 4) return false;
        for (int i = 0; i < n - 1; i++) {
            for (int j = i + 2; j < n - 1; j++) {
                if (i == 0 && j == n - 2 && c.isClosed()) continue;
                if (segmentsCross(p.get(i), p.get(i + 1), p.get(j), p.get(j + 1))) return true;
            }
        }
        return false;
    }

    private boolean segmentsCross(Vec2 a, Vec2 b, Vec2 c, Vec2 d) {
        return orient(a, b, c) * orient(a, b, d) < 0 && orient(c, d, a) * orient(c, d, b) < 0;
    }

    private double orient(Vec2 a, Vec2 b, Vec2 c) {
        return (b.x() - a.x()) * (c.y() - a.y()) - (b.y() - a.y()) * (c.x() - a.x());
    }

    private boolean isTextLikeLayer(String layer) {
        String l = layer == null ? "" : layer.toLowerCase();
        return l.contains("text") || l.contains("dim") || l.contains("defpoints");
    }

    private GeoIssue issue(int n, String cat, String sev, String msg, Contour c, boolean fix) {
        return new GeoIssue("I" + n, cat, sev, msg, c.getId(), List.copyOf(c.getPoints()), fix);
    }
}
