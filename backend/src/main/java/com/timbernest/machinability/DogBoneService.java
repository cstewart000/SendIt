package com.timbernest.machinability;

import com.timbernest.admin.Tool;
import com.timbernest.geometry.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Inserts circular dog-bone arcs at internal corners so a round endmill can fully
 * clear square internal corners. Each dog-bone is a tessellated circular arc.
 *
 * <p>Arcs cut INTO the part material (not out into free space), so a square male
 * feature can seat fully in a female internal corner.
 *
 * <ul>
 *   <li>Outer contours: concave corners → arc into the solid</li>
 *   <li>Holes: corners → arc into the surrounding solid (not the void)</li>
 * </ul>
 */
@Service
public class DogBoneService {
    private static final Logger log = LoggerFactory.getLogger(DogBoneService.class);
    private static final double MIN_EDGE = 0.5;

    public record Result(int corners, double radiusMm) {}

    public Result apply(GeometryModel model, Tool tool, double scale) {
        double rTool = tool.getDiameterMm() / 2.0 * Math.max(0.4, Math.min(scale, 1.5));
        int added = 0;
        for (Ring ring : classify(model)) {
            List<Vec2> pts = ring.pts;
            if (pts.size() < 3) continue;
            List<Vec2> out = new ArrayList<>();
            int n = pts.size();
            for (int i = 0; i < n; i++) {
                Vec2 prev = pts.get((i - 1 + n) % n);
                Vec2 cur = pts.get(i);
                Vec2 next = pts.get((i + 1) % n);
                double edgeIn = prev.dist(cur);
                double edgeOut = cur.dist(next);
                if (edgeIn < MIN_EDGE || edgeOut < MIN_EDGE) {
                    out.add(cur);
                    continue;
                }
                double r = Math.min(rTool, Math.min(edgeIn, edgeOut) * 0.45);
                if (r < 0.25 || !needsDogbone(prev, cur, next, ring.hole, ring.ccw)) {
                    out.add(cur);
                    continue;
                }
                List<Vec2> arc = circularDogbone(prev, cur, next, r, ring.pts, ring.hole);
                if (arc.size() < 3) {
                    out.add(cur);
                    continue;
                }
                out.addAll(arc);
                added++;
            }
            ring.contour.setPoints(dedupe(out, 1e-5));
            ring.contour.setClosed(true);
        }
        log.info("Applied circular dog-bones: {} corners, radius≈{}mm scale={}", added, rTool, scale);
        return new Result(added, rTool);
    }

    public int countCandidates(GeometryModel model) {
        int n = 0;
        for (Ring ring : classify(model)) {
            List<Vec2> pts = ring.pts;
            if (pts.size() < 3) continue;
            for (int i = 0; i < pts.size(); i++) {
                Vec2 prev = pts.get((i - 1 + pts.size()) % pts.size());
                Vec2 cur = pts.get(i);
                Vec2 next = pts.get((i + 1) % pts.size());
                if (prev.dist(cur) < MIN_EDGE || cur.dist(next) < MIN_EDGE) continue;
                if (needsDogbone(prev, cur, next, ring.hole, ring.ccw)) n++;
            }
        }
        return n;
    }

    /**
     * Circular arc dog-bone at corner {@code b}, tangent to edges BA and BC, bulging into waste.
     */
    List<Vec2> circularDogbone(Vec2 a, Vec2 b, Vec2 c, double r, List<Vec2> poly, boolean hole) {
        Vec2 u = unit(a.x() - b.x(), a.y() - b.y());
        Vec2 v = unit(c.x() - b.x(), c.y() - b.y());
        if (u == null || v == null) return List.of();

        double dot = clamp(u.x() * v.x() + u.y() * v.y(), -1, 1);
        double phi = Math.acos(dot);
        if (phi < 0.1 || phi > Math.PI - 0.1) return List.of();

        Vec2 bis = unit(u.x() + v.x(), u.y() + v.y());
        if (bis == null) {
            bis = unit(-u.y(), u.x());
            if (bis == null) return List.of();
        }

        // Dog-bones remove material at internal corners of the PART so a square
        // mating feature fits — the arc must bulge INTO the solid, not into free space.
        // Outer: material = inside poly → centre must be inside.
        // Hole: material = outside hole ring → centre must be outside the hole.
        double half = phi / 2.0;
        double sinHalf = Math.sin(half);
        if (sinHalf < 0.2) sinHalf = 0.2;
        double dist = r / sinHalf;

        Vec2 intoMaterial = bis;
        Vec2 center = new Vec2(b.x() + intoMaterial.x() * dist, b.y() + intoMaterial.y() * dist);
        boolean centerInMaterial = hole ? !pointInPoly(center, poly) : pointInPoly(center, poly);
        if (!centerInMaterial) {
            intoMaterial = new Vec2(-bis.x(), -bis.y());
            center = new Vec2(b.x() + intoMaterial.x() * dist, b.y() + intoMaterial.y() * dist);
        }

        // Tangent points on the two edge lines, clamped near the corner
        Vec2 t1 = projectPointToLine(center, b, a);
        Vec2 t2 = projectPointToLine(center, b, c);
        t1 = clampToRay(b, u, t1, Math.min(r * 0.2, a.dist(b) * 0.1), a.dist(b) * 0.9);
        t2 = clampToRay(b, v, t2, Math.min(r * 0.2, c.dist(b) * 0.1), c.dist(b) * 0.9);
        t1 = pointOnCircle(center, t1, r);
        t2 = pointOnCircle(center, t2, r);

        double a1 = Math.atan2(t1.y() - center.y(), t1.x() - center.x());
        double a2 = Math.atan2(t2.y() - center.y(), t2.x() - center.x());
        // Prefer the arc that bulges deeper into the solid (away from free space)
        double through = Math.atan2(center.y() - b.y(), center.x() - b.x());
        double sweep = chooseSweep(a1, a2, through);

        int segs = Math.max(10, Math.min(24, (int) Math.ceil(Math.abs(sweep) / (Math.PI / 14))));
        List<Vec2> arc = new ArrayList<>(segs + 1);
        for (int i = 0; i <= segs; i++) {
            double t = (double) i / segs;
            double ang = a1 + sweep * t;
            arc.add(new Vec2(center.x() + r * Math.cos(ang), center.y() + r * Math.sin(ang)));
        }
        return arc;
    }

    /** Signed sweep a1→a2 that passes near {@code through} (waste bulge). */
    static double chooseSweep(double a1, double a2, double through) {
        double ccw = normAngle(a2 - a1);
        double cw = ccw - Math.PI * 2;
        double midCcw = normAngle(a1 + ccw / 2);
        double midCw = normAngle(a1 + cw / 2);
        return angleDiff(midCcw, through) <= angleDiff(midCw, through) ? ccw : cw;
    }

    static double normAngle(double a) {
        double t = a % (Math.PI * 2);
        if (t < 0) t += Math.PI * 2;
        return t;
    }

    static double angleDiff(double a, double b) {
        double d = Math.abs(normAngle(a) - normAngle(b));
        return Math.min(d, Math.PI * 2 - d);
    }

    boolean needsDogbone(Vec2 a, Vec2 b, Vec2 c, boolean hole, boolean ccw) {
        double cross = (b.x() - a.x()) * (c.y() - b.y()) - (b.y() - a.y()) * (c.x() - b.x());
        boolean leftTurn = cross > 1e-4;
        boolean rightTurn = cross < -1e-4;
        if (!leftTurn && !rightTurn) return false;
        if (!isSharp(a, b, c)) return false;
        // Outer CCW: internal = right turn; Hole CCW: dogbone on left (convex of hole ring)
        if (!hole) return ccw ? rightTurn : leftTurn;
        return ccw ? leftTurn : rightTurn;
    }

    private boolean isSharp(Vec2 a, Vec2 b, Vec2 c) {
        double ax = a.x() - b.x(), ay = a.y() - b.y();
        double cx = c.x() - b.x(), cy = c.y() - b.y();
        double al = Math.hypot(ax, ay), cl = Math.hypot(cx, cy);
        if (al < 1e-9 || cl < 1e-9) return false;
        double ang = Math.acos(clamp((ax * cx + ay * cy) / (al * cl), -1, 1));
        return ang < Math.PI - 0.12;
    }

    private record Ring(Contour contour, List<Vec2> pts, boolean hole, boolean ccw, double area) {}

    private List<Ring> classify(GeometryModel model) {
        List<Ring> rings = new ArrayList<>();
        for (Contour c : model.getContours()) {
            if (c.getPoints() == null || c.getPoints().size() < 3) continue;
            List<Vec2> pts = dedupe(c.getPoints(), 1e-6);
            if (pts.size() >= 2 && pts.get(0).dist(pts.get(pts.size() - 1)) < 1e-4) {
                pts = new ArrayList<>(pts.subList(0, pts.size() - 1));
            }
            boolean closed = c.isClosed() || (pts.size() >= 3 && pts.get(0).dist(pts.get(pts.size() - 1)) < 0.5);
            if (!closed || pts.size() < 3) continue;
            double sa = signedArea(pts);
            double area = Math.abs(sa);
            if (area < 1.0) continue;
            c.setClosed(true);
            rings.add(new Ring(c, pts, false, sa > 0, area));
        }
        if (rings.isEmpty()) return rings;
        rings.sort(Comparator.comparingDouble(Ring::area).reversed());
        List<Ring> out = new ArrayList<>();
        for (int i = 0; i < rings.size(); i++) {
            Ring r = rings.get(i);
            boolean hole = false;
            Vec2 cen = centroid(r.pts);
            for (int j = 0; j < i; j++) {
                if (pointInPoly(cen, rings.get(j).pts)) {
                    hole = true;
                    break;
                }
            }
            out.add(new Ring(r.contour, r.pts, hole, r.ccw, r.area));
        }
        return out;
    }

    private static Vec2 unit(double x, double y) {
        double L = Math.hypot(x, y);
        if (L < 1e-12) return null;
        return new Vec2(x / L, y / L);
    }

    private static Vec2 projectPointToLine(Vec2 p, Vec2 a, Vec2 b) {
        double vx = b.x() - a.x(), vy = b.y() - a.y();
        double L2 = vx * vx + vy * vy;
        if (L2 < 1e-18) return a;
        double t = ((p.x() - a.x()) * vx + (p.y() - a.y()) * vy) / L2;
        return new Vec2(a.x() + t * vx, a.y() + t * vy);
    }

    private static Vec2 clampToRay(Vec2 origin, Vec2 dirUnit, Vec2 p, double minT, double maxT) {
        double t = (p.x() - origin.x()) * dirUnit.x() + (p.y() - origin.y()) * dirUnit.y();
        t = Math.max(minT, Math.min(maxT, t));
        return new Vec2(origin.x() + dirUnit.x() * t, origin.y() + dirUnit.y() * t);
    }

    private static Vec2 pointOnCircle(Vec2 center, Vec2 approx, double r) {
        double dx = approx.x() - center.x(), dy = approx.y() - center.y();
        double L = Math.hypot(dx, dy);
        if (L < 1e-12) return new Vec2(center.x() + r, center.y());
        return new Vec2(center.x() + dx / L * r, center.y() + dy / L * r);
    }

    private static List<Vec2> dedupe(List<Vec2> pts, double eps) {
        List<Vec2> out = new ArrayList<>();
        for (Vec2 p : pts) {
            if (out.isEmpty() || out.get(out.size() - 1).dist(p) > eps) out.add(p);
        }
        if (out.size() > 2 && out.get(0).dist(out.get(out.size() - 1)) <= eps) {
            out.remove(out.size() - 1);
        }
        return out;
    }

    private static double signedArea(List<Vec2> pts) {
        double a = 0;
        for (int i = 0; i < pts.size(); i++) {
            Vec2 p = pts.get(i), q = pts.get((i + 1) % pts.size());
            a += p.x() * q.y() - q.x() * p.y();
        }
        return a / 2.0;
    }

    private static Vec2 centroid(List<Vec2> pts) {
        double x = 0, y = 0;
        for (Vec2 p : pts) { x += p.x(); y += p.y(); }
        return new Vec2(x / pts.size(), y / pts.size());
    }

    private static boolean pointInPoly(Vec2 p, List<Vec2> poly) {
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

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
