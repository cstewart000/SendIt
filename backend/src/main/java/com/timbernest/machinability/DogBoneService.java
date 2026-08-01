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
 * CNC dog-bone fillets for internal corners (Vectric / Fusion-style).
 *
 * <h2>Why dog-bones</h2>
 * A round endmill cannot cut a sharp internal corner (min radius = tool radius).
 * A dog-bone overcuts into the <b>part material</b> so a square male feature can seat fully.
 *
 * <h2>Geometry (Fusion dog-bone hole)</h2>
 * At corner vertex B with unit edge directions u, v:
 * <pre>
 *   into   = unit bisector into solid material
 *   center = B + into · r          // offset into material by tool radius
 *   radius = r                     // tool radius
 *   // ⇒ original vertex B lies on the circle (outer edge "tangent" to the vertex)
 * </pre>
 * The contour keeps both original straight edges all the way to B, and inserts a full
 * circular lobe (the dog-bone hole) whose rim passes through B and whose body sits
 * in the solid. Non-corner vertices are never moved.
 *
 * <p>This matches the Fusion Dogbone add-in: centreline from corner along the bisector
 * of length r, circle of radius r, corner coincident with the circle.
 */
@Service
public class DogBoneService {
    private static final Logger log = LoggerFactory.getLogger(DogBoneService.class);
    private static final double MIN_EDGE = 1.0;

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
                double lenIn = prev.dist(cur);
                double lenOut = cur.dist(next);
                if (lenIn < MIN_EDGE || lenOut < MIN_EDGE
                        || !needsDogbone(prev, cur, next, ring.hole, ring.ccw)) {
                    out.add(cur);
                    continue;
                }
                double r = Math.min(rTool, Math.min(lenIn, lenOut) * 0.4);
                if (r < 0.3) {
                    out.add(cur);
                    continue;
                }
                List<Vec2> lobe = dogboneLobe(prev, cur, next, r, ring.pts, ring.hole);
                if (lobe.size() < 4) {
                    out.add(cur);
                    continue;
                }
                out.addAll(lobe);
                added++;
            }
            ring.contour.setPoints(dedupe(out, 1e-6));
            ring.contour.setClosed(true);
        }
        log.info("Dog-bones applied: {} corners, r≈{}mm (vertex on circle rim)", added, rTool);
        return new Result(added, rTool);
    }

    public int countCandidates(GeometryModel model) {
        int n = 0;
        for (Ring ring : classify(model)) {
            List<Vec2> pts = ring.pts;
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
     * Fusion-style dog-bone at corner B: circle of radius r whose centre is offset into
     * material by r, so the original vertex lies on the circumference.
     * Returns {@code [B, ...circle samples..., B]} so both original edges still meet at B.
     */
    List<Vec2> dogboneLobe(Vec2 a, Vec2 b, Vec2 c, double r, List<Vec2> poly, boolean hole) {
        Vec2 u = unit(a.x() - b.x(), a.y() - b.y());
        Vec2 v = unit(c.x() - b.x(), c.y() - b.y());
        if (u == null || v == null) return List.of();

        double cosPhi = clamp(u.x() * v.x() + u.y() * v.y(), -1, 1);
        double phi = Math.acos(cosPhi);
        if (phi < Math.toRadians(25) || phi > Math.toRadians(160)) return List.of();

        if (r < 0.25 || a.dist(b) < r * 1.05 || c.dist(b) < r * 1.05) {
            // Need enough edge to keep geometry stable around the lobe
            r = Math.min(r, Math.min(a.dist(b), c.dist(b)) * 0.4);
            if (r < 0.25) return List.of();
        }

        Vec2 into = intoMaterial(u, v, b, r, poly, hole);
        if (into == null) return List.of();

        // Centre offset into solid by r ⇒ original vertex B is on the circle rim
        Vec2 center = new Vec2(b.x() + into.x() * r, b.y() + into.y() * r);

        double a0 = Math.atan2(b.y() - center.y(), b.x() - center.x());
        // Full circle; pick winding so the lobe body sits in material (and removes solid)
        double sweep = pickFullSweep(center, r, a0, poly, hole);
        if (sweep == 0) return List.of();

        int segs = Math.max(20, Math.min(36, (int) Math.ceil(Math.abs(sweep) / (Math.PI / 16))));
        List<Vec2> lobe = new ArrayList<>(segs + 2);
        // Start at original vertex — edges BA / BC remain exact straight lines into B
        lobe.add(b);
        for (int i = 1; i < segs; i++) {
            double ang = a0 + sweep * ((double) i / segs);
            lobe.add(arcPoint(center, r, ang));
        }
        // End at original vertex again so the outgoing edge BC is unchanged
        lobe.add(b);
        return lobe;
    }

    /**
     * Unit direction from corner into solid material along the angle bisector.
     */
    private Vec2 intoMaterial(Vec2 u, Vec2 v, Vec2 b, double r, List<Vec2> poly, boolean hole) {
        Vec2 bis = unit(u.x() + v.x(), u.y() + v.y());
        if (bis == null) bis = unit(-u.y(), u.x());
        if (bis == null) return null;

        // Probe a point slightly along +bis and -bis; pick the one in material
        Vec2 pPos = new Vec2(b.x() + bis.x() * r * 0.5, b.y() + bis.y() * r * 0.5);
        Vec2 pNeg = new Vec2(b.x() - bis.x() * r * 0.5, b.y() - bis.y() * r * 0.5);
        boolean posMat = inMaterial(pPos, poly, hole);
        boolean negMat = inMaterial(pNeg, poly, hole);
        if (posMat && !negMat) return bis;
        if (negMat && !posMat) return new Vec2(-bis.x(), -bis.y());
        if (posMat) return bis;
        if (negMat) return new Vec2(-bis.x(), -bis.y());
        return null;
    }

    /**
     * Full ±2π sweep starting at a0. Prefer the winding whose interior (disk) is material
     * so the lobe overcuts solid, and a sample opposite B is in material.
     */
    private double pickFullSweep(Vec2 center, double r, double a0, List<Vec2> poly, boolean hole) {
        // Point opposite the vertex on the circle (deepest into the offset direction)
        Vec2 opposite = arcPoint(center, r, a0 + Math.PI);
        if (!inMaterial(opposite, poly, hole)) {
            // Should still be material for a correct into-offset; try both winds via sample
        }
        // For outer solid, a CW loop (negative) attached on a CCW contour treats the disk
        // as exterior (material removed). Verify with a mid-side sample.
        double cw = -Math.PI * 2;
        double ccw = Math.PI * 2;
        Vec2 midCw = arcPoint(center, r, a0 + cw / 2.0);   // = opposite
        Vec2 midCcw = arcPoint(center, r, a0 + ccw / 2.0); // = opposite too for full circle
        // Both full sweeps share the same points; orientation matters for winding only.
        // Use a quarter-turn sample to decide orientation relative to material.
        Vec2 qCw = arcPoint(center, r, a0 + cw * 0.25);
        Vec2 qCcw = arcPoint(center, r, a0 + ccw * 0.25);
        boolean qCwMat = inMaterial(qCw, poly, hole);
        boolean qCcwMat = inMaterial(qCcw, poly, hole);
        if (qCwMat && !qCcwMat) return cw;
        if (qCcwMat && !qCwMat) return ccw;
        // Prefer the wind that keeps opposite in material (both should)
        if (inMaterial(opposite, poly, hole)) return cw;
        return 0;
    }

    private static boolean inMaterial(Vec2 p, List<Vec2> poly, boolean hole) {
        boolean inside = pointInPoly(p, poly);
        return hole ? !inside : inside;
    }

    private static Vec2 arcPoint(Vec2 center, double radius, double ang) {
        return new Vec2(center.x() + radius * Math.cos(ang), center.y() + radius * Math.sin(ang));
    }

    boolean needsDogbone(Vec2 a, Vec2 b, Vec2 c, boolean hole, boolean ccw) {
        double cross = (b.x() - a.x()) * (c.y() - b.y()) - (b.y() - a.y()) * (c.x() - b.x());
        boolean left = cross > 1e-4;
        boolean right = cross < -1e-4;
        if (!left && !right) return false;
        if (!isSharp(a, b, c)) return false;
        if (!hole) return ccw ? right : left;
        return ccw ? left : right;
    }

    private boolean isSharp(Vec2 a, Vec2 b, Vec2 c) {
        double ax = a.x() - b.x(), ay = a.y() - b.y();
        double cx = c.x() - b.x(), cy = c.y() - b.y();
        double al = Math.hypot(ax, ay), cl = Math.hypot(cx, cy);
        if (al < 1e-9 || cl < 1e-9) return false;
        double ang = Math.acos(clamp((ax * cx + ay * cy) / (al * cl), -1, 1));
        return ang < Math.PI - 0.15;
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
            boolean closed = c.isClosed()
                    || (pts.size() >= 3 && pts.get(0).dist(pts.get(pts.size() - 1)) < 0.5);
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
