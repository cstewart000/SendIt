package com.timbernest.cam;

import com.timbernest.geometry.model.Vec2;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Closed-polygon offset via parallel edges + corner intersection.
 * Rings are normalized to CCW; positive delta expands, negative contracts.
 */
public final class ContourOffset {
    private ContourOffset() {}

    /** Expand outer profile by tool radius (tool center outside part). */
    public static List<Vec2> outerToolpath(List<Vec2> pts, double radius) {
        return offsetClosed(pts, Math.abs(radius));
    }

    /** Inset hole profile by tool radius (tool center inside void). */
    public static List<Vec2> holeToolpath(List<Vec2> pts, double radius) {
        return offsetClosed(pts, -Math.abs(radius));
    }

    /**
     * Offset a closed ring. Positive {@code delta} expands, negative contracts,
     * independent of input winding (normalized to CCW first).
     */
    public static List<Vec2> offsetClosed(List<Vec2> pts, double delta) {
        if (pts == null || pts.size() < 3 || Math.abs(delta) < 1e-9) {
            return pts == null ? List.of() : new ArrayList<>(pts);
        }
        List<Vec2> ring = dedupe(pts);
        if (ring.size() < 3) return new ArrayList<>(pts);
        if (!isCcw(ring)) Collections.reverse(ring);

        int n = ring.size();
        // Offset each edge start along outward normal (right of CCW edge)
        Vec2[] edgeStart = new Vec2[n];
        Vec2[] edgeDir = new Vec2[n];
        for (int i = 0; i < n; i++) {
            Vec2 a = ring.get(i);
            Vec2 b = ring.get((i + 1) % n);
            double dx = b.x() - a.x(), dy = b.y() - a.y();
            double len = Math.hypot(dx, dy);
            if (len < 1e-9) {
                edgeStart[i] = a;
                edgeDir[i] = new Vec2(0, 0);
                continue;
            }
            double nx = dy / len, ny = -dx / len; // outward for CCW
            edgeStart[i] = new Vec2(a.x() + nx * delta, a.y() + ny * delta);
            edgeDir[i] = new Vec2(dx, dy);
        }
        List<Vec2> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            Vec2 a0 = edgeStart[i];
            Vec2 a1 = new Vec2(a0.x() + edgeDir[i].x(), a0.y() + edgeDir[i].y());
            Vec2 b0 = edgeStart[j];
            Vec2 b1 = new Vec2(b0.x() + edgeDir[j].x(), b0.y() + edgeDir[j].y());
            Vec2 hit = lineIntersect(a0, a1, b0, b1);
            out.add(hit != null ? hit : b0);
        }
        return out;
    }

    public static boolean isCcw(List<Vec2> pts) {
        return signedArea(pts) > 0;
    }

    public static double signedArea(List<Vec2> pts) {
        double a = 0;
        for (int i = 0; i < pts.size(); i++) {
            Vec2 p = pts.get(i), q = pts.get((i + 1) % pts.size());
            a += p.x() * q.y() - q.x() * p.y();
        }
        return a / 2.0;
    }

    public static double absArea(List<Vec2> pts) {
        return Math.abs(signedArea(pts));
    }

    static List<Vec2> dedupe(List<Vec2> pts) {
        List<Vec2> out = new ArrayList<>();
        for (Vec2 p : pts) {
            if (out.isEmpty() || out.get(out.size() - 1).dist(p) > 1e-6) out.add(p);
        }
        if (out.size() > 1 && out.get(0).dist(out.get(out.size() - 1)) < 1e-6) {
            out.remove(out.size() - 1);
        }
        return out;
    }

    /** Intersection of infinite lines a0→a1 and b0→b1. */
    static Vec2 lineIntersect(Vec2 a0, Vec2 a1, Vec2 b0, Vec2 b1) {
        double a1x = a1.x() - a0.x(), a1y = a1.y() - a0.y();
        double b1x = b1.x() - b0.x(), b1y = b1.y() - b0.y();
        double den = a1x * b1y - a1y * b1x;
        if (Math.abs(den) < 1e-12) return null;
        double t = ((b0.x() - a0.x()) * b1y - (b0.y() - a0.y()) * b1x) / den;
        return new Vec2(a0.x() + t * a1x, a0.y() + t * a1y);
    }
}
