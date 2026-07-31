package com.timbernest.cam;

import com.timbernest.admin.Machine;
import com.timbernest.admin.Material;
import com.timbernest.admin.Tool;
import com.timbernest.geometry.model.GeometryModel;
import com.timbernest.geometry.model.Vec2;
import com.timbernest.nesting.NestMath;
import com.timbernest.nesting.NestPlacement;
import com.timbernest.nesting.NestResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/**
 * LinuxCNC profile generator: tool-radius offline offset, holes-before-outer,
 * multi-pass depth, machine safe-Z.
 */
@Component
public class GCodeGenerator {
    private static final Logger log = LoggerFactory.getLogger(GCodeGenerator.class);

    public String generate(NestResult nest, List<GeometryModel> models, List<NestPlacement> ignored,
                           Machine machine, Tool tool, Material material) {
        double radius = Math.max(0, tool.getDiameterMm() / 2.0);
        double depth = material.getThicknessMm();
        double feed = machine.getDefaultFeedMmMin();
        double plungeFeed = feed / 2.0;
        double step = Math.max(0.1, Math.min(tool.getMaxDepthMm(), depth));
        int passes = Math.max(1, (int) Math.ceil(depth / step));
        double safeZ = safeZ(machine);

        StringBuilder sb = new StringBuilder();
        sb.append("(SendIt LinuxCNC profile)\n");
        sb.append(String.format(Locale.US, "(Tool: %s Ø%.2f  offset=%.3f mm offline)\n",
                tool.getName(), tool.getDiameterMm(), radius));
        sb.append(String.format(Locale.US, "(Passes: %d x %.3f mm  depth=%.3f)\n", passes, step, depth));
        sb.append(String.format(Locale.US, "(Order: holes first, outer last; safeZ=%.1f)\n", safeZ));
        sb.append("G21 G90 G17\nG40 G49 G80\n");
        sb.append(String.format(Locale.US, "M6 T1\nS%.0f M3\n", machine.getDefaultSpeedRpm()));
        sb.append(String.format(Locale.US, "G0 Z%.3f\n", safeZ));

        PathMetrics.Result metrics = PathMetrics.compute(nest, models, machine, tool, material);
        int i = 0;
        for (NestPlacement pl : nest.getPlacements()) {
            GeometryModel model = models.get(Math.min(i, models.size() - 1));
            i++;
            double[] origin = bboxMin(model);
            sb.append(String.format(Locale.US, "(Part %s sheet=%d rot=%.0f)\n",
                    pl.getLabel() != null ? pl.getLabel() : "?", pl.getSheetIndex(), pl.getRotationDeg()));
            for (PathMetrics.CutContour cc : PathMetrics.orderedContours(model, radius)) {
                writeContour(sb, cc.points(), pl, origin, passes, step, depth, feed, plungeFeed, safeZ,
                        cc.hole());
            }
        }
        sb.append(String.format(Locale.US, "G0 Z%.3f\nM5\nM30\n", safeZ));
        log.info("Generated G-code chars={} cutMm={} contours={} passes={}",
                sb.length(), Math.round(metrics.cutLengthMm()), metrics.contourCount(), passes);
        return sb.toString();
    }

    private void writeContour(StringBuilder sb, List<Vec2> pts, NestPlacement pl, double[] origin,
                              int passes, double step, double depth, double feed, double plungeFeed,
                              double safeZ, boolean hole) {
        if (pts.size() < 2) return;
        Vec2 first = transform(pts.get(0), pl, origin);
        sb.append(String.format(Locale.US, "(%s)\n", hole ? "hole" : "outer"));
        sb.append(String.format(Locale.US, "G0 X%.3f Y%.3f\n", first.x(), first.y()));
        for (int p = 1; p <= passes; p++) {
            double z = -Math.min(depth, p * step);
            sb.append(String.format(Locale.US, "G1 Z%.3f F%.0f\n", z, plungeFeed));
            for (int i = 1; i < pts.size(); i++) {
                Vec2 v = transform(pts.get(i), pl, origin);
                sb.append(String.format(Locale.US, "G1 X%.3f Y%.3f F%.0f\n", v.x(), v.y(), feed));
            }
            sb.append(String.format(Locale.US, "G1 X%.3f Y%.3f F%.0f\n", first.x(), first.y(), feed));
        }
        sb.append(String.format(Locale.US, "G0 Z%.3f\n", safeZ));
    }

    /** Localize to part origin, rotate about center, place AABB at (pl.x, pl.y). */
    Vec2 transform(Vec2 p, NestPlacement pl, double[] origin) {
        double nw = pl.getNativeWidth() > 0 ? pl.getNativeWidth() : pl.getWidth();
        double nh = pl.getNativeHeight() > 0 ? pl.getNativeHeight() : pl.getHeight();
        double[] box = NestMath.aabb(nw, nh, pl.getRotationDeg());
        double lx = p.x() - origin[0], ly = p.y() - origin[1];
        double rad = Math.toRadians(pl.getRotationDeg());
        double c = Math.cos(rad), s = Math.sin(rad);
        double dx = lx - nw / 2, dy = ly - nh / 2;
        double rx = dx * c - dy * s;
        double ry = dx * s + dy * c;
        return new Vec2(rx + pl.getX() + box[0] / 2, ry + pl.getY() + box[1] / 2);
    }

    static double safeZ(Machine machine) {
        double z = machine.getWorkZmm() > 0 ? Math.min(15.0, machine.getWorkZmm()) : 15.0;
        return Math.max(5.0, z);
    }

    private double[] bboxMin(GeometryModel model) {
        double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY;
        for (var c : model.getContours()) {
            for (Vec2 p : c.getPoints()) {
                minX = Math.min(minX, p.x());
                minY = Math.min(minY, p.y());
            }
        }
        if (!Double.isFinite(minX)) return new double[]{0, 0};
        return new double[]{minX, minY};
    }
}
