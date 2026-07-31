package com.timbernest.cam;

import com.timbernest.admin.Machine;
import com.timbernest.admin.Tool;
import com.timbernest.geometry.model.Vec2;
import com.timbernest.nesting.NestPlacement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Places wood-screw pilot holes in sheet waste around nested parts.
 * Smart rules: outside outer toolpath, ≥ min dist from toolpath edge, on sheet, not under parts.
 */
public final class FixingPlanner {
    private static final Logger log = LoggerFactory.getLogger(FixingPlanner.class);
    private FixingPlanner() {}

    public record Candidate(String id, int sheetIndex, double x, double y, double diameterMm,
                            int placementIndex, String label) {}

    /**
     * @param outerWorldPaths outer profile polylines in sheet coords (tool centreline), one per placement
     */
    public static List<Candidate> plan(List<NestPlacement> placements,
                                       List<List<Vec2>> outerWorldPaths,
                                       Machine machine, Tool tool,
                                       double sheetW, double sheetH,
                                       double margin) {
        if (!machine.isFixingsEnabled()) return List.of();
        double toolR = Math.max(0, tool.getDiameterMm() / 2.0);
        double minEdge = machine.getFixingMinToolDistanceMm() > 0
                ? machine.getFixingMinToolDistanceMm() : 10;
        // Distance from hole centre to toolpath centreline must clear tool radius + min edge
        double minToCenterline = minEdge + toolR;
        double holeD = machine.getFixingHoleDiameterMm() > 0 ? machine.getFixingHoleDiameterMm() : 4;
        double holeR = holeD / 2.0;
        List<Candidate> out = new ArrayList<>();

        for (int pi = 0; pi < placements.size(); pi++) {
            NestPlacement pl = placements.get(pi);
            List<Vec2> outer = pi < outerWorldPaths.size() ? outerWorldPaths.get(pi) : List.of();
            List<double[]> candidates = candidatesAround(pl, outer, minToCenterline + holeR + 1);
            String label = pl.getLabel() != null ? pl.getLabel() : ("P" + pi);
            int kept = 0;
            for (int ci = 0; ci < candidates.size(); ci++) {
                double cx = candidates.get(ci)[0], cy = candidates.get(ci)[1];
                if (cx < margin + holeR || cy < margin + holeR
                        || cx > sheetW - margin - holeR || cy > sheetH - margin - holeR) continue;
                if (outer.size() >= 3 && pointInPoly(new Vec2(cx, cy), outer)) continue;
                if (minDistToPolyline(cx, cy, outer) < minToCenterline) continue;
                // Not inside any other placement AABB (expanded)
                if (hitsAnyPart(cx, cy, holeR, placements, pi, toolR + 1)) continue;
                // Not too close to an accepted fixing
                if (nearExisting(cx, cy, out, Math.max(holeD * 3, 15))) continue;
                String id = "fix-" + pi + "-" + ci;
                out.add(new Candidate(id, pl.getSheetIndex(), cx, cy, holeD, pi, label));
                kept++;
                // Cap density: small parts 2, medium 3, large 4
                double perim = Math.max(pl.getWidth(), pl.getHeight());
                int maxN = perim < 80 ? 2 : perim < 200 ? 3 : 4;
                if (kept >= maxN) break;
            }
        }
        log.info("FixingPlanner placed {} screw holes (minEdge={} toolR={})", out.size(), minEdge, toolR);
        return out;
    }

    public static List<Candidate> applyDisabled(List<Candidate> all, Set<String> disabled) {
        if (disabled == null || disabled.isEmpty()) return all;
        List<Candidate> out = new ArrayList<>();
        for (Candidate c : all) if (!disabled.contains(c.id())) out.add(c);
        return out;
    }

    /** Side midpoints + corners offset outward from placement AABB. */
    private static List<double[]> candidatesAround(NestPlacement pl, List<Vec2> outer, double offset) {
        double x0 = pl.getX(), y0 = pl.getY();
        double x1 = x0 + pl.getWidth(), y1 = y0 + pl.getHeight();
        double mx = (x0 + x1) / 2, my = (y0 + y1) / 2;
        double o = Math.max(offset, 8);
        List<double[]> c = new ArrayList<>();
        // Side midpoints (prefer for clamps)
        c.add(new double[]{mx, y0 - o});
        c.add(new double[]{mx, y1 + o});
        c.add(new double[]{x0 - o, my});
        c.add(new double[]{x1 + o, my});
        // Corners
        c.add(new double[]{x0 - o * 0.75, y0 - o * 0.75});
        c.add(new double[]{x1 + o * 0.75, y0 - o * 0.75});
        c.add(new double[]{x0 - o * 0.75, y1 + o * 0.75});
        c.add(new double[]{x1 + o * 0.75, y1 + o * 0.75});
        // If we have outer polyline, also sample outward normals at quarter points
        if (outer != null && outer.size() >= 4) {
            int n = outer.size();
            for (int k = 0; k < 4; k++) {
                int i = (k * n) / 4;
                Vec2 a = outer.get(i);
                Vec2 b = outer.get((i + 1) % n);
                double dx = b.x() - a.x(), dy = b.y() - a.y();
                double len = Math.hypot(dx, dy);
                if (len < 1e-6) continue;
                // outward normal for CCW outer = right of edge = (dy, -dx)
                double nx = dy / len, ny = -dx / len;
                // ensure points away from centroid
                double cx = 0, cy = 0;
                for (Vec2 p : outer) { cx += p.x(); cy += p.y(); }
                cx /= n; cy /= n;
                double midx = (a.x() + b.x()) / 2, midy = (a.y() + b.y()) / 2;
                if ((midx - cx) * nx + (midy - cy) * ny < 0) { nx = -nx; ny = -ny; }
                c.add(new double[]{midx + nx * o, midy + ny * o});
            }
        }
        return c;
    }

    private static boolean hitsAnyPart(double cx, double cy, double holeR,
                                       List<NestPlacement> placements, int self, double pad) {
        for (int i = 0; i < placements.size(); i++) {
            if (i == self) continue;
            NestPlacement p = placements.get(i);
            if (cx + holeR < p.getX() - pad || cy + holeR < p.getY() - pad
                    || cx - holeR > p.getX() + p.getWidth() + pad
                    || cy - holeR > p.getY() + p.getHeight() + pad) continue;
            return true;
        }
        return false;
    }

    private static boolean nearExisting(double x, double y, List<Candidate> existing, double min) {
        for (Candidate c : existing) {
            if (Math.hypot(c.x() - x, c.y() - y) < min) return true;
        }
        return false;
    }

    static double minDistToPolyline(double x, double y, List<Vec2> poly) {
        if (poly == null || poly.size() < 2) return Double.POSITIVE_INFINITY;
        double best = Double.POSITIVE_INFINITY;
        Vec2 p = new Vec2(x, y);
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

    static boolean pointInPoly(Vec2 p, List<Vec2> poly) {
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
}
