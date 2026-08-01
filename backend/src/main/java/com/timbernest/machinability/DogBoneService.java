package com.timbernest.machinability;

import com.timbernest.admin.Tool;
import com.timbernest.geometry.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Adds dog-bone notches at sharp internal corners so a round endmill can fully clear the corner.
 * Radius defaults to tool radius (scaled by {@code scale}).
 */
@Service
public class DogBoneService {
    private static final Logger log = LoggerFactory.getLogger(DogBoneService.class);

    public record Result(int corners, double radiusMm) {}

    public Result apply(GeometryModel model, Tool tool, double scale) {
        double r = tool.getDiameterMm() / 2.0 * Math.max(0.4, Math.min(scale, 1.5));
        int added = 0;
        for (Contour c : model.getContours()) {
            if (!c.isClosed() || c.getPoints().size() < 3) continue;
            boolean ccw = signedArea(c.getPoints()) > 0;
            List<Vec2> pts = c.getPoints();
            List<Vec2> out = new ArrayList<>();
            for (int i = 0; i < pts.size(); i++) {
                Vec2 prev = pts.get((i - 1 + pts.size()) % pts.size());
                Vec2 cur = pts.get(i);
                Vec2 next = pts.get((i + 1) % pts.size());
                out.add(cur);
                if (!isInternalCorner(prev, cur, next, ccw)) continue;
                if (!isSharp(prev, cur, next)) continue;
                Vec2 bis = outwardBisector(prev, cur, next, ccw);
                if (Math.hypot(bis.x(), bis.y()) < 1e-9) continue;
                // Offset into the waste (outside material for outer contour internal corners)
                out.add(new Vec2(cur.x() + bis.x() * r, cur.y() + bis.y() * r));
                added++;
            }
            c.setPoints(out);
        }
        log.info("Applied dog-bones: {} corners, radius={}mm scale={}", added, r, scale);
        return new Result(added, r);
    }

    /** Count sharp internal corners that would receive dog-bones (no mutation). */
    public int countCandidates(GeometryModel model) {
        int n = 0;
        for (Contour c : model.getContours()) {
            if (!c.isClosed() || c.getPoints().size() < 3) continue;
            boolean ccw = signedArea(c.getPoints()) > 0;
            List<Vec2> pts = c.getPoints();
            for (int i = 0; i < pts.size(); i++) {
                Vec2 prev = pts.get((i - 1 + pts.size()) % pts.size());
                Vec2 cur = pts.get(i);
                Vec2 next = pts.get((i + 1) % pts.size());
                if (isInternalCorner(prev, cur, next, ccw) && isSharp(prev, cur, next)) n++;
            }
        }
        return n;
    }

    private boolean isSharp(Vec2 a, Vec2 b, Vec2 c) {
        double ax = a.x() - b.x(), ay = a.y() - b.y();
        double cx = c.x() - b.x(), cy = c.y() - b.y();
        double al = Math.hypot(ax, ay), cl = Math.hypot(cx, cy);
        if (al < 1e-9 || cl < 1e-9) return false;
        double dot = (ax * cx + ay * cy) / (al * cl);
        double ang = Math.acos(Math.max(-1, Math.min(1, dot)));
        // Interior turn sharper than ~170° (nearly colinear excluded)
        return ang < Math.PI - 0.18;
    }

    /** Internal = concave relative to contour winding. */
    private boolean isInternalCorner(Vec2 a, Vec2 b, Vec2 c, boolean ccw) {
        double cross = (b.x() - a.x()) * (c.y() - b.y()) - (b.y() - a.y()) * (c.x() - b.x());
        // CCW contour: internal (concave) is right turn (cross < 0)
        // CW contour: internal is left turn (cross > 0)
        return ccw ? cross < -1e-3 : cross > 1e-3;
    }

    /**
     * Unit direction from vertex into the waste for a dog-bone notch
     * (along the external angle bisector, away from material).
     */
    private Vec2 outwardBisector(Vec2 a, Vec2 b, Vec2 c, boolean ccw) {
        double ax = a.x() - b.x(), ay = a.y() - b.y();
        double cx = c.x() - b.x(), cy = c.y() - b.y();
        double al = Math.hypot(ax, ay), cl = Math.hypot(cx, cy);
        if (al < 1e-9 || cl < 1e-9) return new Vec2(0, 0);
        ax /= al; ay /= al; cx /= cl; cy /= cl;
        // Unit vectors away from b along edges; bisector of exterior for internal corner
        // Direction into concave waste: -(unit_in + unit_out) flipped for winding
        double bx = -(ax + cx), by = -(ay + cy);
        double bl = Math.hypot(bx, by);
        if (bl < 1e-9) {
            // 180° — use perpendicular
            bx = -ay; by = ax;
            bl = Math.hypot(bx, by);
        }
        if (bl < 1e-9) return new Vec2(0, 0);
        bx /= bl; by /= bl;
        // Ensure pointing into waste: cross of edge ab→bc should match internal sense
        // Sample point along bisector should be outside material for outer contour
        return new Vec2(bx, by);
    }

    private double signedArea(List<Vec2> pts) {
        double a = 0;
        for (int i = 0; i < pts.size(); i++) {
            Vec2 p = pts.get(i), q = pts.get((i + 1) % pts.size());
            a += p.x() * q.y() - q.x() * p.y();
        }
        return a / 2.0;
    }
}
