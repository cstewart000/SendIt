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
 * CNC dog-bone fillets for internal corners.
 *
 * <h2>Why dog-bones</h2>
 * A round endmill cannot cut a sharp internal corner (minimum radius = tool radius).
 * Without relief, a square male feature will not seat fully. A dog-bone overcuts
 * into the <b>part material</b> at that corner (Vectric / Fusion-style) so the mate fits.
 *
 * <h2>Geometry (classic cartoon dog-bone)</h2>
 * At corner B with unit directions {@code u}, {@code v} along the two original edges
 * toward the neighboring vertices:
 * <pre>
 *   setback  = tool radius r  (clamped to edge length)
 *   p1       = B + u · setback     // still on original edge BA
 *   p2       = B + v · setback     // still on original edge BC
 *   center   = B
 *   radius   = setback
 *   arc      = major arc p1 → p2 that travels through SOLID material
 * </pre>
 * For a 90° free-space corner that is a 270° circular lobe of radius r into the solid.
 *
 * <h2>Critical rule</h2>
 * Original straight edges are never moved or bent. Only the single corner vertex is
 * replaced by the arc; endpoints lie exactly on the original edge segments, so every
 * other point on those edges stays collinear with the design intent.
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
                    // Keep original vertex exactly — straight edges unchanged
                    out.add(cur);
                    continue;
                }
                double r = Math.min(rTool, Math.min(lenIn, lenOut) * 0.4);
                if (r < 0.3) {
                    out.add(cur);
                    continue;
                }
                List<Vec2> arc = dogboneArc(prev, cur, next, r, ring.pts, ring.hole);
                if (arc.size() < 3) {
                    out.add(cur);
                    continue;
                }
                out.addAll(arc);
                added++;
            }
            ring.contour.setPoints(dedupe(out, 1e-6));
            ring.contour.setClosed(true);
        }
        log.info("Dog-bones applied: {} corners, r≈{}mm (original edges preserved)", added, rTool);
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
     * Classic dog-bone replacing corner B only.
     * Returns {@code [p1, ...arc samples..., p2]} where p1/p2 lie on the original edges.
     * Does not include B. Radius equals setback (≈ tool radius).
     */
    List<Vec2> dogboneArc(Vec2 a, Vec2 b, Vec2 c, double r, List<Vec2> poly, boolean hole) {
        Vec2 u = unit(a.x() - b.x(), a.y() - b.y()); // along edge toward prev
        Vec2 v = unit(c.x() - b.x(), c.y() - b.y()); // along edge toward next
        if (u == null || v == null) return List.of();

        double cosPhi = clamp(u.x() * v.x() + u.y() * v.y(), -1, 1);
        double phi = Math.acos(cosPhi);
        // φ is the free-space angle between the two edge rays; internal corners are acute–obtuse
        if (phi < Math.toRadians(25) || phi > Math.toRadians(160)) return List.of();

        // Endpoints stay exactly on BA and BC — this is what keeps original straight lines intact
        double setback = Math.min(r, Math.min(a.dist(b), c.dist(b)) * 0.4);
        if (setback < 0.25) return List.of();

        Vec2 p1 = new Vec2(b.x() + u.x() * setback, b.y() + u.y() * setback);
        Vec2 p2 = new Vec2(b.x() + v.x() * setback, b.y() + v.y() * setback);

        // Circle centered at the corner, radius = setback (classic cartoon dog-bone)
        double a1 = Math.atan2(p1.y() - b.y(), p1.x() - b.x());
        double a2 = Math.atan2(p2.y() - b.y(), p2.x() - b.x());

        double ccw = normAngle(a2 - a1);       // (0, 2π]
        if (ccw < 1e-9) ccw = Math.PI * 2;
        double cw = ccw - Math.PI * 2;         // [-2π, 0)

        Vec2 midCcw = arcPoint(b, setback, a1 + ccw / 2.0);
        Vec2 midCw = arcPoint(b, setback, a1 + cw / 2.0);
        boolean ccwInMat = inMaterial(midCcw, poly, hole);
        boolean cwInMat = inMaterial(midCw, poly, hole);

        double sweep;
        if (ccwInMat && !cwInMat) {
            sweep = ccw;
        } else if (cwInMat && !ccwInMat) {
            sweep = cw;
        } else if (ccwInMat && cwInMat) {
            // Prefer the major arc (classic knuckle) when both mids test as material
            sweep = Math.abs(ccw) >= Math.abs(cw) ? ccw : cw;
        } else {
            return List.of(); // neither side is material — not a usable corner
        }

        // Defensive: mid of chosen sweep must be solid
        if (!inMaterial(arcPoint(b, setback, a1 + sweep / 2.0), poly, hole)) {
            return List.of();
        }

        int segs = Math.max(12, Math.min(28, (int) Math.ceil(Math.abs(sweep) / (Math.PI / 14))));
        List<Vec2> arc = new ArrayList<>(segs + 1);
        arc.add(p1); // exact on original edge BA
        for (int i = 1; i < segs; i++) {
            double t = (double) i / segs;
            double ang = a1 + sweep * t;
            arc.add(arcPoint(b, setback, ang));
        }
        arc.add(p2); // exact on original edge BC
        return arc;
    }

    private static boolean inMaterial(Vec2 p, List<Vec2> poly, boolean hole) {
        boolean inside = pointInPoly(p, poly);
        return hole ? !inside : inside;
    }

    private static Vec2 arcPoint(Vec2 center, double radius, double ang) {
        return new Vec2(center.x() + radius * Math.cos(ang), center.y() + radius * Math.sin(ang));
    }

    static double normAngle(double a) {
        double t = a % (Math.PI * 2);
        if (t < 0) t += Math.PI * 2;
        return t;
    }

    boolean needsDogbone(Vec2 a, Vec2 b, Vec2 c, boolean hole, boolean ccw) {
        double cross = (b.x() - a.x()) * (c.y() - b.y()) - (b.y() - a.y()) * (c.x() - b.x());
        boolean left = cross > 1e-4;
        boolean right = cross < -1e-4;
        if (!left && !right) return false;
        if (!isSharp(a, b, c)) return false;
        // Outer CCW solid: material left of edges → concave = right turn
        // Hole CCW void: material outside → dog-bone where hole turns left into plate
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
