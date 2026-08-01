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
 * CNC dog-bone fillets for <b>internal</b> corners (Vectric / Fusion-style).
 *
 * <h2>Where dog-bones apply</h2>
 * Only at corners where the solid occupies a reflex region (&gt;180°), so a round
 * endmill would leave uncut material that blocks a square mate:
 * <ul>
 *   <li><b>Outer contours</b> — concave (re-entrant) corners only</li>
 *   <li><b>Holes / pockets</b> — corners of the cutout, overcutting into the plate</li>
 * </ul>
 * Convex outer corners never receive dog-bones.
 *
 * <h2>Geometry</h2>
 * At internal corner B:
 * <pre>
 *   into   = unit direction into solid material (along angle bisector)
 *   center = B + into · r     // offset into material by tool radius
 *   radius = r                // tool radius
 *   // original vertex B lies on the circle rim (Fusion-style)
 * </pre>
 * Original straight edges still meet at B; a circular lobe into the solid is inserted.
 */
@Service
public class DogBoneService {
    private static final Logger log = LoggerFactory.getLogger(DogBoneService.class);
    private static final double MIN_EDGE = 1.0;

    public record Result(int corners, double radiusMm) {}

    public Result apply(GeometryModel model, Tool tool, double scale) {
        double rTool = tool.getDiameterMm() / 2.0 * Math.max(0.4, Math.min(scale, 1.5));
        int added = 0;
        List<Ring> rings = classify(model);
        // Outers first (largest) — used as material context for hole corners
        List<List<Vec2>> outers = rings.stream()
                .filter(r -> !r.hole)
                .map(r -> r.pts)
                .toList();

        for (Ring ring : rings) {
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
                        || !isInternalCorner(prev, cur, next, ring.hole, ring.ccw)) {
                    out.add(cur);
                    continue;
                }
                double r = Math.min(rTool, Math.min(lenIn, lenOut) * 0.4);
                if (r < 0.3) {
                    out.add(cur);
                    continue;
                }
                List<Vec2> lobe = dogboneLobe(prev, cur, next, r, ring.pts, ring.hole, outers);
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
        log.info("Dog-bones applied: {} internal corners, r≈{}mm", added, rTool);
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
                if (isInternalCorner(prev, cur, next, ring.hole, ring.ccw)) n++;
            }
        }
        return n;
    }

    /**
     * Fusion-style dog-bone at internal corner B: circle of radius r, centre offset into
     * material by r, so the original vertex lies on the circumference.
     * Returns {@code [B, ...circle samples..., B]}.
     */
    List<Vec2> dogboneLobe(Vec2 a, Vec2 b, Vec2 c, double r, List<Vec2> poly, boolean hole) {
        return dogboneLobe(a, b, c, r, poly, hole, List.of());
    }

    List<Vec2> dogboneLobe(Vec2 a, Vec2 b, Vec2 c, double r, List<Vec2> poly, boolean hole,
                           List<List<Vec2>> outers) {
        Vec2 u = unit(a.x() - b.x(), a.y() - b.y()); // along edge toward prev
        Vec2 v = unit(c.x() - b.x(), c.y() - b.y()); // along edge toward next
        if (u == null || v == null) return List.of();

        double cosPhi = clamp(u.x() * v.x() + u.y() * v.y(), -1, 1);
        double phi = Math.acos(cosPhi);
        // Free-space angle between the two edge rays at an internal corner
        if (phi < Math.toRadians(20) || phi > Math.toRadians(170)) return List.of();

        if (a.dist(b) < r * 1.05 || c.dist(b) < r * 1.05) {
            r = Math.min(r, Math.min(a.dist(b), c.dist(b)) * 0.4);
            if (r < 0.25) return List.of();
        }

        Vec2 into = intoMaterial(u, v, b, r, poly, hole, outers);
        if (into == null) return List.of();

        // Centre in solid; original vertex on the rim
        Vec2 center = new Vec2(b.x() + into.x() * r, b.y() + into.y() * r);

        // Verify centre really is in material (internal overcut)
        if (!inMaterial(center, poly, hole, outers)) {
            into = new Vec2(-into.x(), -into.y());
            center = new Vec2(b.x() + into.x() * r, b.y() + into.y() * r);
            if (!inMaterial(center, poly, hole, outers)) return List.of();
        }

        double a0 = Math.atan2(b.y() - center.y(), b.x() - center.x());
        double sweep = pickFullSweep(center, r, a0, poly, hole, outers);
        if (sweep == 0) return List.of();

        int segs = Math.max(20, Math.min(36, (int) Math.ceil(Math.abs(sweep) / (Math.PI / 16))));
        List<Vec2> lobe = new ArrayList<>(segs + 2);
        lobe.add(b);
        for (int i = 1; i < segs; i++) {
            double ang = a0 + sweep * ((double) i / segs);
            lobe.add(arcPoint(center, r, ang));
        }
        lobe.add(b);
        return lobe;
    }

    /**
     * Internal corner of the solid: where a square male would bind without overcut.
     * <ul>
     *   <li>Outer (solid inside): concave turns only</li>
     *   <li>Hole (solid outside): corners that open into the plate (convex on the void)</li>
     * </ul>
     */
    boolean isInternalCorner(Vec2 a, Vec2 b, Vec2 c, boolean hole, boolean ccw) {
        double cross = (b.x() - a.x()) * (c.y() - b.y()) - (b.y() - a.y()) * (c.x() - b.x());
        boolean left = cross > 1e-4;
        boolean right = cross < -1e-4;
        if (!left && !right) return false;
        if (!isSharp(a, b, c)) return false;

        // Outer CCW: material left → internal/concave = right turn
        // Outer CW:  material right → internal/concave = left turn
        // Hole CCW (void left): corners that bite into plate = left turn (convex void)
        // Hole CW  (void right): convex void = right turn
        if (!hole) return ccw ? right : left;
        return ccw ? left : right;
    }

    /** Back-compat name used by older tests. */
    boolean needsDogbone(Vec2 a, Vec2 b, Vec2 c, boolean hole, boolean ccw) {
        return isInternalCorner(a, b, c, hole, ccw);
    }

    /**
     * Unit direction from corner into solid material.
     * Uses the angle bisector of the edge rays, validated with point-in-material tests.
     * For holes, material is outside the hole (and inside an outer ring when known).
     */
    private Vec2 intoMaterial(Vec2 u, Vec2 v, Vec2 b, double r, List<Vec2> poly, boolean hole,
                              List<List<Vec2>> outers) {
        // Bisector of the two edge directions (pointing into free-space angle or material)
        Vec2 bis = unit(u.x() + v.x(), u.y() + v.y());
        if (bis == null) {
            // 180° — use perpendicular
            bis = unit(-u.y(), u.x());
        }
        if (bis == null) return null;

        double probe = Math.max(r * 0.5, 0.5);
        Vec2 pPos = new Vec2(b.x() + bis.x() * probe, b.y() + bis.y() * probe);
        Vec2 pNeg = new Vec2(b.x() - bis.x() * probe, b.y() - bis.y() * probe);
        boolean posMat = inMaterial(pPos, poly, hole, outers);
        boolean negMat = inMaterial(pNeg, poly, hole, outers);

        if (posMat && !negMat) return bis;
        if (negMat && !posMat) return new Vec2(-bis.x(), -bis.y());

        // Ambiguous near boundary — use path normals into solid
        Vec2 into = solidInward(u, v, hole);
        if (into != null) {
            Vec2 p = new Vec2(b.x() + into.x() * probe, b.y() + into.y() * probe);
            if (inMaterial(p, poly, hole, outers)) return into;
            Vec2 flipped = new Vec2(-into.x(), -into.y());
            p = new Vec2(b.x() + flipped.x() * probe, b.y() + flipped.y() * probe);
            if (inMaterial(p, poly, hole, outers)) return flipped;
        }

        if (posMat) return bis;
        if (negMat) return new Vec2(-bis.x(), -bis.y());
        return null;
    }

    /**
     * Inward direction into solid from edge rays u,v (both point away from corner along edges).
     * For a CCW outer, solid is to the right of u when looking from B along free-space… 
     * Simpler: cross(u,v) &gt; 0 means v is left of u; free-space sector is the smaller phi.
     * Solid is opposite the free-space bisector for an internal corner.
     */
    private static Vec2 solidInward(Vec2 u, Vec2 v, boolean hole) {
        Vec2 bis = unit(u.x() + v.x(), u.y() + v.y());
        if (bis == null) return unit(-u.y(), u.x());
        // Free-space bisector is +bis when u,v span the free-space angle (acute/obtuse <180).
        // Internal corner: solid is opposite free space for outer; for hole, solid is outside void
        // which is also opposite the free-space (void) bisector when u,v span the void angle.
        // Both outer-internal and hole-corner: solid is -bis when +bis points into free/void.
        // Caller validates with inMaterial; here we return -bis as the usual solid side.
        return new Vec2(-bis.x(), -bis.y());
    }

    private double pickFullSweep(Vec2 center, double r, double a0, List<Vec2> poly, boolean hole,
                                 List<List<Vec2>> outers) {
        Vec2 opposite = arcPoint(center, r, a0 + Math.PI);
        double cw = -Math.PI * 2;
        double ccw = Math.PI * 2;
        Vec2 qCw = arcPoint(center, r, a0 + cw * 0.25);
        Vec2 qCcw = arcPoint(center, r, a0 + ccw * 0.25);
        boolean qCwMat = inMaterial(qCw, poly, hole, outers);
        boolean qCcwMat = inMaterial(qCcw, poly, hole, outers);
        if (qCwMat && !qCcwMat) return cw;
        if (qCcwMat && !qCwMat) return ccw;
        // Entire circle sits in material (typical internal dog-bone) — prefer CW
        if (inMaterial(opposite, poly, hole, outers)) return cw;
        // Last resort: still emit a circle if centre is material
        if (inMaterial(center, poly, hole, outers)) return cw;
        return 0;
    }

    /**
     * Point is in solid material:
     * <ul>
     *   <li>Outer ring: inside the outer polygon</li>
     *   <li>Hole ring: outside the hole, and inside at least one outer when known</li>
     * </ul>
     */
    private static boolean inMaterial(Vec2 p, List<Vec2> poly, boolean hole, List<List<Vec2>> outers) {
        if (!hole) {
            return pointInPoly(p, poly);
        }
        // Hole: material is the plate outside the cutout
        if (pointInPoly(p, poly)) return false; // still in the void
        if (outers == null || outers.isEmpty()) {
            return true; // no outer context — treat outside hole as material
        }
        for (List<Vec2> outer : outers) {
            if (pointInPoly(p, outer)) return true;
        }
        return false;
    }

    private static Vec2 arcPoint(Vec2 center, double radius, double ang) {
        return new Vec2(center.x() + radius * Math.cos(ang), center.y() + radius * Math.sin(ang));
    }

    private boolean isSharp(Vec2 a, Vec2 b, Vec2 c) {
        double ax = a.x() - b.x(), ay = a.y() - b.y();
        double cx = c.x() - b.x(), cy = c.y() - b.y();
        double al = Math.hypot(ax, ay), cl = Math.hypot(cx, cy);
        if (al < 1e-9 || cl < 1e-9) return false;
        double ang = Math.acos(clamp((ax * cx + ay * cy) / (al * cl), -1, 1));
        // Internal free-space corners are typically 20°–170°
        return ang > Math.toRadians(20) && ang < Math.PI - 0.12;
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
            // Prefer a point guaranteed inside the ring (not centroid — fails for C/L shapes)
            Vec2 probe = interiorProbe(r.pts);
            for (int j = 0; j < i; j++) {
                if (pointInPoly(probe, rings.get(j).pts)) {
                    hole = true;
                    break;
                }
            }
            out.add(new Ring(r.contour, r.pts, hole, r.ccw, r.area));
        }
        return out;
    }

    /**
     * Point guaranteed inside a simple ring: average of a vertex and the ring centroid,
     * nudged slightly — more reliable than raw centroid for concave rings.
     */
    private static Vec2 interiorProbe(List<Vec2> pts) {
        Vec2 cen = centroid(pts);
        if (pointInPoly(cen, pts)) return cen;
        // Try midpoints of vertex→centroid segments
        for (Vec2 p : pts) {
            Vec2 mid = new Vec2((p.x() + cen.x()) * 0.5, (p.y() + cen.y()) * 0.5);
            if (pointInPoly(mid, pts)) return mid;
        }
        // Fallback: slight offset from first edge midpoint toward centroid
        Vec2 a = pts.get(0), b = pts.get(1 % pts.size());
        Vec2 mid = new Vec2((a.x() + b.x()) * 0.5, (a.y() + b.y()) * 0.5);
        Vec2 dir = unit(cen.x() - mid.x(), cen.y() - mid.y());
        if (dir != null) {
            Vec2 p = new Vec2(mid.x() + dir.x() * 0.1, mid.y() + dir.y() * 0.1);
            if (pointInPoly(p, pts)) return p;
        }
        return cen;
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
