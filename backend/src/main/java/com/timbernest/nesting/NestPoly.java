package com.timbernest.nesting;

import com.timbernest.geometry.model.Contour;
import com.timbernest.geometry.model.GeometryModel;
import com.timbernest.geometry.model.Vec2;

import java.util.ArrayList;
import java.util.List;

/** Polygon helpers for true-shape nesting. */
public final class NestPoly {
    private NestPoly() {}

    /** Outer ring localized to bbox min (largest closed contour). */
    public static List<Vec2> outerLocal(GeometryModel model) {
        Contour best = null;
        double bestA = -1;
        for (Contour c : model.getContours()) {
            if (!c.isClosed() || c.getPoints().size() < 3) continue;
            double[] b = c.bbox();
            double a = Math.max(0, (b[2] - b[0]) * (b[3] - b[1]));
            if (a > bestA) { bestA = a; best = c; }
        }
        if (best == null) return List.of();
        double[] b = best.bbox();
        List<Vec2> out = new ArrayList<>();
        for (Vec2 p : best.getPoints()) out.add(new Vec2(p.x() - b[0], p.y() - b[1]));
        return out;
    }

    /** World ring for placement AABB origin (x,y) and rotation about native center. */
    public static List<Vec2> world(List<Vec2> local, double nw, double nh, double x, double y, double deg) {
        double[] box = NestMath.aabb(nw, nh, deg);
        double rad = Math.toRadians(deg);
        double c = Math.cos(rad), s = Math.sin(rad);
        List<Vec2> out = new ArrayList<>(local.size());
        for (Vec2 p : local) {
            double dx = p.x() - nw / 2, dy = p.y() - nh / 2;
            double rx = dx * c - dy * s, ry = dx * s + dy * c;
            out.add(new Vec2(rx + x + box[0] / 2, ry + y + box[1] / 2));
        }
        return out;
    }

    public static double[] bounds(List<Vec2> poly) {
        double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;
        for (Vec2 p : poly) {
            minX = Math.min(minX, p.x()); minY = Math.min(minY, p.y());
            maxX = Math.max(maxX, p.x()); maxY = Math.max(maxY, p.y());
        }
        return new double[]{minX, minY, maxX, maxY};
    }

    public static boolean collide(List<Vec2> a, List<Vec2> b, double gap) {
        if (a.isEmpty() || b.isEmpty()) return false;
        double[] ab = bounds(a), bb = bounds(b);
        if (ab[2] + gap < bb[0] || bb[2] + gap < ab[0]
                || ab[3] + gap < bb[1] || bb[3] + gap < ab[1]) return false;
        if (polygonsIntersect(a, b)) return true;
        return minDist(a, b) < gap - 1e-6;
    }

    static boolean polygonsIntersect(List<Vec2> a, List<Vec2> b) {
        int n = a.size(), m = b.size();
        for (int i = 0; i < n; i++) {
            Vec2 a1 = a.get(i), a2 = a.get((i + 1) % n);
            for (int j = 0; j < m; j++) {
                if (segmentsCross(a1, a2, b.get(j), b.get((j + 1) % m))) return true;
            }
        }
        return pointIn(a.get(0), b) || pointIn(b.get(0), a);
    }

    static boolean segmentsCross(Vec2 a, Vec2 b, Vec2 c, Vec2 d) {
        double d1 = cross(a, b, c), d2 = cross(a, b, d), d3 = cross(c, d, a), d4 = cross(c, d, b);
        return ((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0))
                && ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0));
    }

    static double cross(Vec2 a, Vec2 b, Vec2 p) {
        return (b.x() - a.x()) * (p.y() - a.y()) - (b.y() - a.y()) * (p.x() - a.x());
    }

    static boolean pointIn(Vec2 p, List<Vec2> poly) {
        boolean inside = false;
        for (int i = 0, j = poly.size() - 1; i < poly.size(); j = i++) {
            Vec2 a = poly.get(i), b = poly.get(j);
            if (((a.y() > p.y()) != (b.y() > p.y()))
                    && (p.x() < (b.x() - a.x()) * (p.y() - a.y()) / (b.y() - a.y() + 1e-12) + a.x())) {
                inside = !inside;
            }
        }
        return inside;
    }

    static double minDist(List<Vec2> a, List<Vec2> b) {
        double best = Double.POSITIVE_INFINITY;
        for (Vec2 p : a) best = Math.min(best, distToEdges(p, b));
        for (Vec2 p : b) best = Math.min(best, distToEdges(p, a));
        return best;
    }

    static double distToEdges(Vec2 p, List<Vec2> poly) {
        double best = Double.POSITIVE_INFINITY;
        for (int i = 0; i < poly.size(); i++) {
            Vec2 a = poly.get(i), b = poly.get((i + 1) % poly.size());
            best = Math.min(best, distPointSeg(p, a, b));
        }
        return best;
    }

    static double distPointSeg(Vec2 p, Vec2 a, Vec2 b) {
        double vx = b.x() - a.x(), vy = b.y() - a.y();
        double wx = p.x() - a.x(), wy = p.y() - a.y();
        double c1 = vx * wx + vy * wy;
        if (c1 <= 0) return p.dist(a);
        double c2 = vx * vx + vy * vy;
        if (c2 <= c1) return p.dist(b);
        double t = c1 / c2;
        return p.dist(new Vec2(a.x() + t * vx, a.y() + t * vy));
    }
}
