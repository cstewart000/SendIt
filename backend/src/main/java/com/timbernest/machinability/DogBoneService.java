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
 * CNC dog-bone fillets for internal corners (Fusion / Vectric style).
 *
 * <h2>Geometry</h2>
 * At internal corner B with unit edge directions {@code u}, {@code v} (along the edges
 * away from B):
 * <pre>
 *   free   = unit bisector into FREE SPACE (the open angle of the corner)
 *   center = B + free · r          // centre sits in free space, NOT in the solid
 *   radius = r                     // tool radius
 *   // ⇒ original vertex B lies on the circle rim
 *   p1, p2 = second intersections of the circle with edges BA, BC
 *   arc    = p1 → p2 through SOLID (the overcut into material)
 * </pre>
 * This matches the Fusion Dogbone add-in: centreline from the corner along the free-space
 * bisector of length r, circle of radius r, corner coincident with the circle, cut into
 * the solid where the disk overlaps the part.
 *
 * <p>Original straight edges beyond p1/p2 are unchanged.
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
                double r = Math.min(rTool, Math.min(lenIn, lenOut) * 0.35);
                if (r < 0.3) {
                    out.add(cur);
                    continue;
                }
                List<Vec2> lobe = dogboneLobe(prev, cur, next, r, ring.pts, ring.hole, outers);
                if (lobe.size() < 3) {
                    out.add(cur);
                    continue;
                }
                out.addAll(lobe);
                added++;
            }
            ring.contour.setPoints(dedupe(out, 1e-6));
            ring.contour.setClosed(true);
        }
        log.info("Dog-bones applied: {} internal corners, r≈{}mm (centre in free space)", added, rTool);
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
     * Dog-bone at internal corner B. Centre in free space; vertex on rim; arc overcuts solid.
     * Returns {@code [p1, ...arc samples..., p2]} on the original edges.
     */
    List<Vec2> dogboneLobe(Vec2 a, Vec2 b, Vec2 c, double r, List<Vec2> poly, boolean hole) {
        return dogboneLobe(a, b, c, r, poly, hole, List.of());
    }

    List<Vec2> dogboneLobe(Vec2 a, Vec2 b, Vec2 c, double r, List<Vec2> poly, boolean hole,
                           List<List<Vec2>> outers) {
        Vec2 u = unit(a.x() - b.x(), a.y() - b.y()); // toward prev along edge
        Vec2 v = unit(c.x() - b.x(), c.y() - b.y()); // toward next along edge
        if (u == null || v == null) return List.of();

        double cosPhi = clamp(u.x() * v.x() + u.y() * v.y(), -1, 1);
        double phi = Math.acos(cosPhi);
        if (phi < Math.toRadians(20) || phi > Math.toRadians(170)) return List.of();

        // Free-space bisector of the corner (u,v span the open free-space angle)
        Vec2 free = unit(u.x() + v.x(), u.y() + v.y());
        if (free == null) free = unit(-u.y(), u.x());
        if (free == null) return List.of();

        // Ensure free points into FREE SPACE (not solid)
        double probe = Math.max(r * 0.5, 0.5);
        Vec2 pFree = new Vec2(b.x() + free.x() * probe, b.y() + free.y() * probe);
        Vec2 pMat = new Vec2(b.x() - free.x() * probe, b.y() - free.y() * probe);
        boolean freeIsFree = !inMaterial(pFree, poly, hole, outers);
        boolean matIsMat = inMaterial(pMat, poly, hole, outers);
        if (!freeIsFree && matIsMat) {
            // bisector was pointing into solid — flip
            free = new Vec2(-free.x(), -free.y());
        } else if (!freeIsFree) {
            // try flip anyway if free probe is in material
            Vec2 flipped = new Vec2(-free.x(), -free.y());
            Vec2 p2 = new Vec2(b.x() + flipped.x() * probe, b.y() + flipped.y() * probe);
            if (!inMaterial(p2, poly, hole, outers)) free = flipped;
            else return List.of();
        }

        // Centre in free space; original vertex on the rim
        Vec2 center = new Vec2(b.x() + free.x() * r, b.y() + free.y() * r);
        if (inMaterial(center, poly, hole, outers)) {
            // Still wrong — abort rather than cut the wrong side
            return List.of();
        }

        // Second intersections of the circle with the two edge rays (t=0 is B)
        // |B + t·dir − C|² = r², C = B + free·r  ⇒  t = 2 r (dir · free)
        double t1 = 2.0 * r * (u.x() * free.x() + u.y() * free.y());
        double t2 = 2.0 * r * (v.x() * free.x() + v.y() * free.y());
        if (t1 < 0.25 || t2 < 0.25) return List.of();
        t1 = Math.min(t1, a.dist(b) * 0.45);
        t2 = Math.min(t2, c.dist(b) * 0.45);
        if (t1 < 0.25 || t2 < 0.25) return List.of();

        Vec2 p1 = new Vec2(b.x() + u.x() * t1, b.y() + u.y() * t1);
        Vec2 p2 = new Vec2(b.x() + v.x() * t2, b.y() + v.y() * t2);

        // Project endpoints onto the true circle (after clamping setback)
        p1 = projectToCircle(center, r, p1);
        p2 = projectToCircle(center, r, p2);

        double a1 = Math.atan2(p1.y() - center.y(), p1.x() - center.x());
        double a2 = Math.atan2(p2.y() - center.y(), p2.x() - center.x());
        double aB = Math.atan2(b.y() - center.y(), b.x() - center.x());

        // Prefer the sweep from p1 to p2 that passes through solid (and typically through B)
        double sweep = chooseSweepThrough(a1, a2, aB, center, r, poly, hole, outers);
        if (sweep == 0) return List.of();

        int segs = Math.max(10, Math.min(28, (int) Math.ceil(Math.abs(sweep) / (Math.PI / 14))));
        List<Vec2> arc = new ArrayList<>(segs + 1);
        arc.add(p1); // exact on / near original edge
        for (int i = 1; i < segs; i++) {
            double ang = a1 + sweep * ((double) i / segs);
            arc.add(arcPoint(center, r, ang));
        }
        arc.add(p2);
        return arc;
    }

    /**
     * Choose CCW or CW sweep from a1 to a2. Prefer the path that:
     * 1) passes near the original vertex angle aB, and
     * 2) has its midpoint in solid material (the overcut).
     */
    private double chooseSweepThrough(double a1, double a2, double aB, Vec2 center, double r,
                                      List<Vec2> poly, boolean hole, List<List<Vec2>> outers) {
        double ccw = normAngle(a2 - a1);
        if (ccw < 1e-9) ccw = Math.PI * 2;
        double cw = ccw - Math.PI * 2;

        Vec2 midCcw = arcPoint(center, r, a1 + ccw / 2.0);
        Vec2 midCw = arcPoint(center, r, a1 + cw / 2.0);
        boolean ccwMat = inMaterial(midCcw, poly, hole, outers);
        boolean cwMat = inMaterial(midCw, poly, hole, outers);

        // Material mid wins
        if (ccwMat && !cwMat) return ccw;
        if (cwMat && !ccwMat) return cw;

        // Both or neither: pick the sweep whose mid-angle is closer to vertex angle aB
        // (the rim touches the vertex on the solid-facing side of the free-space centre)
        double midAngCcw = a1 + ccw / 2.0;
        double midAngCw = a1 + cw / 2.0;
        if (angleDiff(midAngCcw, aB) <= angleDiff(midAngCw, aB)) {
            return ccwMat || !cwMat ? ccw : cw;
        }
        return cwMat || !ccwMat ? cw : ccw;
    }

    private static Vec2 projectToCircle(Vec2 center, double r, Vec2 p) {
        double dx = p.x() - center.x(), dy = p.y() - center.y();
        double L = Math.hypot(dx, dy);
        if (L < 1e-12) return p;
        return new Vec2(center.x() + r * dx / L, center.y() + r * dy / L);
    }

    private static double normAngle(double a) {
        double t = a % (Math.PI * 2);
        if (t < 0) t += Math.PI * 2;
        return t;
    }

    private static double angleDiff(double a, double b) {
        double d = Math.abs(normAngle(a) - normAngle(b));
        return Math.min(d, Math.PI * 2 - d);
    }

    boolean isInternalCorner(Vec2 a, Vec2 b, Vec2 c, boolean hole, boolean ccw) {
        double cross = (b.x() - a.x()) * (c.y() - b.y()) - (b.y() - a.y()) * (c.x() - b.x());
        boolean left = cross > 1e-4;
        boolean right = cross < -1e-4;
        if (!left && !right) return false;
        if (!isSharp(a, b, c)) return false;
        if (!hole) return ccw ? right : left;
        return ccw ? left : right;
    }

    boolean needsDogbone(Vec2 a, Vec2 b, Vec2 c, boolean hole, boolean ccw) {
        return isInternalCorner(a, b, c, hole, ccw);
    }

    private static boolean inMaterial(Vec2 p, List<Vec2> poly, boolean hole, List<List<Vec2>> outers) {
        if (!hole) {
            return pointInPoly(p, poly);
        }
        if (pointInPoly(p, poly)) return false; // void
        if (outers == null || outers.isEmpty()) return true;
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

    private static Vec2 interiorProbe(List<Vec2> pts) {
        Vec2 cen = centroid(pts);
        if (pointInPoly(cen, pts)) return cen;
        for (Vec2 p : pts) {
            Vec2 mid = new Vec2((p.x() + cen.x()) * 0.5, (p.y() + cen.y()) * 0.5);
            if (pointInPoly(mid, pts)) return mid;
        }
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
