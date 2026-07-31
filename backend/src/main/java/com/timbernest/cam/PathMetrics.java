package com.timbernest.cam;

import com.timbernest.admin.Machine;
import com.timbernest.admin.Material;
import com.timbernest.admin.Tool;
import com.timbernest.geometry.model.Contour;
import com.timbernest.geometry.model.GeometryModel;
import com.timbernest.geometry.model.Vec2;
import com.timbernest.nesting.NestPlacement;
import com.timbernest.nesting.NestResult;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Shared cycle-time / path-length estimates used by CAM and quoting.
 * Counts multi-pass cut length, plunges, and sheet-change overhead.
 */
public final class PathMetrics {
    private PathMetrics() {}

    public record Result(
            double cutLengthMm,
            double plungeMm,
            int contourCount,
            int passes,
            double cycleMinutes
    ) {}

    public static Result compute(NestResult nest, List<GeometryModel> models,
                                 Machine machine, Tool tool, Material material) {
        double radius = Math.max(0, tool.getDiameterMm() / 2.0);
        double depth = material.getThicknessMm();
        double step = Math.max(0.1, Math.min(tool.getMaxDepthMm(), depth));
        int passes = Math.max(1, (int) Math.ceil(depth / step));
        double feed = Math.max(1, machine.getDefaultFeedMmMin());
        double plungeFeed = feed / 2.0;

        double cutMm = 0;
        double plungeMm = 0;
        int contours = 0;
        List<NestPlacement> placements = nest.getPlacements();
        for (int i = 0; i < placements.size(); i++) {
            GeometryModel model = models.get(Math.min(i, models.size() - 1));
            for (CutContour cc : orderedContours(model, radius)) {
                double loop = loopLength(cc.points());
                cutMm += loop * passes;
                plungeMm += depth; // one full depth stack of plunges (approx)
                contours++;
            }
        }
        double cutMin = cutMm / feed;
        double plungeMin = plungeMm / Math.max(1, plungeFeed);
        double sheetMin = nest.getSheetCount() * 2.0;
        double cycleMin = cutMin + plungeMin + sheetMin;
        return new Result(cutMm, plungeMm, contours, passes, cycleMin);
    }

    /** Contours ordered holes-first then outer, with tool-radius offset applied. */
    public static List<CutContour> orderedContours(GeometryModel model, double radius) {
        List<Raw> closed = new ArrayList<>();
        for (Contour c : model.getContours()) {
            if (!c.isClosed() || c.getPoints().size() < 3) continue;
            closed.add(new Raw(c, ContourOffset.absArea(c.getPoints())));
        }
        if (closed.isEmpty()) return List.of();
        closed.sort(Comparator.comparingDouble(Raw::area).reversed());
        Raw outer = closed.get(0);
        List<CutContour> out = new ArrayList<>();
        // Holes: smaller closed contours whose centroid lies inside outer
        List<Raw> holes = new ArrayList<>();
        for (int i = 1; i < closed.size(); i++) {
            Raw r = closed.get(i);
            if (centroidInside(r.contour().getPoints(), outer.contour().getPoints())) {
                holes.add(r);
            }
        }
        holes.sort(Comparator.comparingDouble(Raw::area)); // small holes first
        for (Raw h : holes) {
            List<Vec2> path = radius > 0
                    ? ContourOffset.holeToolpath(h.contour().getPoints(), radius)
                    : new ArrayList<>(h.contour().getPoints());
            if (path.size() >= 3 && ContourOffset.absArea(path) > 1.0) {
                out.add(new CutContour(h.contour().getId(), true, path));
            }
        }
        List<Vec2> outerPath = radius > 0
                ? ContourOffset.outerToolpath(outer.contour().getPoints(), radius)
                : new ArrayList<>(outer.contour().getPoints());
        if (outerPath.size() >= 3) {
            out.add(new CutContour(outer.contour().getId(), false, outerPath));
        }
        return out;
    }

    public record CutContour(String id, boolean hole, List<Vec2> points) {}

    private record Raw(Contour contour, double area) {}

    static double loopLength(List<Vec2> pts) {
        if (pts.size() < 2) return 0;
        double len = 0;
        for (int i = 1; i < pts.size(); i++) len += pts.get(i - 1).dist(pts.get(i));
        len += pts.get(pts.size() - 1).dist(pts.get(0));
        return len;
    }

    static boolean centroidInside(List<Vec2> poly, List<Vec2> outer) {
        double cx = 0, cy = 0;
        for (Vec2 p : poly) { cx += p.x(); cy += p.y(); }
        cx /= poly.size();
        cy /= poly.size();
        return pointIn(new Vec2(cx, cy), outer);
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
}
