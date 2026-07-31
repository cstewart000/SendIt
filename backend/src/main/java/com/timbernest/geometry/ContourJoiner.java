package com.timbernest.geometry;

import com.timbernest.geometry.model.Contour;
import com.timbernest.geometry.model.GeometryModel;
import com.timbernest.geometry.model.Vec2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Greedy join of open contours that share endpoints.
 * CAD exports (esp. Voron-style LINE+ARC chains) need this before part extraction;
 * without it only CIRCLEs are closed → 3D shows a cylinder.
 */
public final class ContourJoiner {
    private static final Logger log = LoggerFactory.getLogger(ContourJoiner.class);

    private ContourJoiner() {}

    /**
     * Multi-pass join with adaptive tolerances derived from model size.
     * Safe to call repeatedly on already-joined geometry.
     */
    public static int joinAdaptive(GeometryModel model) {
        double[] b = model.bbox();
        double diag = Math.hypot(Math.max(0, b[2] - b[0]), Math.max(0, b[3] - b[1]));
        if (!Double.isFinite(diag) || diag < 1e-6) diag = 100;
        // 0.25–3 mm base; scale slightly with part size (CAD float noise + arc tessellation)
        double base = Math.max(0.25, Math.min(3.0, diag * 0.002));
        int merges = 0;
        for (double mult : new double[]{1, 2, 4, 8, 16}) {
            merges += join(model, base * mult);
        }
        int closed = sealOpen(model, base * 20);
        log.info("ContourJoiner adaptive: merges={} sealed={} baseEps={}mm contours={}",
                merges, closed, Math.round(base * 100) / 100.0, model.getContours().size());
        return merges + closed;
    }

    public static int join(GeometryModel model, double eps) {
        List<Contour> open = new ArrayList<>();
        List<Contour> closed = new ArrayList<>();
        for (Contour c : model.getContours()) {
            if (c.getPoints().size() < 2) continue;
            if (c.isClosed() && c.getPoints().size() >= 3) closed.add(c);
            else open.add(c);
        }
        boolean changed = true;
        int merges = 0;
        while (changed) {
            changed = false;
            outer:
            for (int i = 0; i < open.size(); i++) {
                for (int j = i + 1; j < open.size(); j++) {
                    Contour merged = tryMerge(open.get(i), open.get(j), eps);
                    if (merged == null) continue;
                    // Seal if endpoints now meet
                    sealIfNear(merged, eps * 2);
                    if (merged.isClosed()) {
                        closed.add(merged);
                        open.remove(j);
                        open.remove(i);
                    } else {
                        open.set(i, merged);
                        open.remove(j);
                    }
                    merges++;
                    changed = true;
                    break outer;
                }
            }
        }
        List<Contour> all = new ArrayList<>(closed);
        all.addAll(open);
        model.setContours(all);
        return merges;
    }

    /** Close open contours whose ends are within eps. */
    public static int sealOpen(GeometryModel model, double eps) {
        int n = 0;
        for (Contour c : model.getContours()) {
            if (sealIfNear(c, eps)) n++;
        }
        return n;
    }

    private static boolean sealIfNear(Contour c, double eps) {
        if (c.isClosed() || c.getPoints().size() < 3) return false;
        Vec2 a = c.getPoints().get(0);
        Vec2 b = c.getPoints().get(c.getPoints().size() - 1);
        if (a.dist(b) > eps) return false;
        c.getPoints().set(c.getPoints().size() - 1, a);
        c.setClosed(true);
        return true;
    }

    private static Contour tryMerge(Contour a, Contour b, double eps) {
        List<Vec2> ap = a.getPoints(), bp = b.getPoints();
        if (ap.size() < 2 || bp.size() < 2) return null;
        Vec2 a0 = ap.get(0), a1 = ap.get(ap.size() - 1);
        Vec2 b0 = bp.get(0), b1 = bp.get(bp.size() - 1);
        Contour out = new Contour();
        out.setId(a.getId() != null ? a.getId() : b.getId());
        out.setLayer(a.getLayer() != null ? a.getLayer() : b.getLayer());
        List<Vec2> pts = new ArrayList<>();
        if (a1.dist(b0) <= eps) {
            pts.addAll(ap);
            snapLast(pts, b0);
            pts.addAll(bp.subList(1, bp.size()));
        } else if (a1.dist(b1) <= eps) {
            pts.addAll(ap);
            snapLast(pts, b1);
            for (int i = bp.size() - 2; i >= 0; i--) pts.add(bp.get(i));
        } else if (a0.dist(b1) <= eps) {
            pts.addAll(bp);
            snapLast(pts, a0);
            pts.addAll(ap.subList(1, ap.size()));
        } else if (a0.dist(b0) <= eps) {
            for (int i = bp.size() - 1; i >= 0; i--) pts.add(bp.get(i));
            snapLast(pts, a0);
            pts.addAll(ap.subList(1, ap.size()));
        } else return null;
        // Drop near-duplicate consecutive points
        out.setPoints(dedupe(pts, Math.max(1e-6, eps * 0.05)));
        return out.getPoints().size() >= 2 ? out : null;
    }

    private static void snapLast(List<Vec2> pts, Vec2 other) {
        if (pts.isEmpty()) return;
        Vec2 last = pts.get(pts.size() - 1);
        pts.set(pts.size() - 1, new Vec2((last.x() + other.x()) / 2, (last.y() + other.y()) / 2));
    }

    private static List<Vec2> dedupe(List<Vec2> pts, double eps) {
        List<Vec2> out = new ArrayList<>();
        for (Vec2 p : pts) {
            if (out.isEmpty() || out.get(out.size() - 1).dist(p) > eps) out.add(p);
        }
        return out;
    }
}
