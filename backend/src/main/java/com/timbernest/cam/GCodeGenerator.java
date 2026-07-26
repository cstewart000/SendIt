package com.timbernest.cam;

import com.timbernest.admin.Machine;
import com.timbernest.admin.Material;
import com.timbernest.admin.Tool;
import com.timbernest.geometry.model.Contour;
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

@Component
public class GCodeGenerator {
    private static final Logger log = LoggerFactory.getLogger(GCodeGenerator.class);

    public String generate(NestResult nest, List<GeometryModel> models, List<NestPlacement> ignored,
                           Machine machine, Tool tool, Material material) {
        StringBuilder sb = new StringBuilder();
        sb.append("(SendIt LinuxCNC profile)\n");
        sb.append(String.format(Locale.US, "(Tool: %s Ø%.2f)\n", tool.getName(), tool.getDiameterMm()));
        sb.append("G21 G90 G17\nG40 G49 G80\n");
        sb.append(String.format(Locale.US, "M6 T1\nS%.0f M3\n", machine.getDefaultSpeedRpm()));
        sb.append("G0 Z15\n");

        double depth = material.getThicknessMm();
        double feed = machine.getDefaultFeedMmMin();
        double step = Math.min(tool.getMaxDepthMm(), depth);
        int passes = (int) Math.ceil(depth / step);

        int i = 0;
        for (NestPlacement pl : nest.getPlacements()) {
            GeometryModel model = models.get(Math.min(i, models.size() - 1));
            i++;
            double[] origin = bboxMin(model);
            for (Contour c : model.getContours()) {
                if (c.getPoints().size() < 2) continue;
                writeContour(sb, c, pl, origin, passes, step, depth, feed);
            }
        }
        sb.append("G0 Z15\nM5\nM30\n");
        log.info("Generated G-code chars={}", sb.length());
        return sb.toString();
    }

    private void writeContour(StringBuilder sb, Contour c, NestPlacement pl, double[] origin,
                              int passes, double step, double depth, double feed) {
        List<Vec2> pts = c.getPoints();
        Vec2 first = transform(pts.get(0), pl, origin);
        sb.append(String.format(Locale.US, "G0 X%.3f Y%.3f\n", first.x(), first.y()));
        for (int p = 1; p <= passes; p++) {
            double z = -Math.min(depth, p * step);
            sb.append(String.format(Locale.US, "G1 Z%.3f F%.0f\n", z, feed / 2));
            for (int i = 1; i < pts.size(); i++) {
                Vec2 v = transform(pts.get(i), pl, origin);
                sb.append(String.format(Locale.US, "G1 X%.3f Y%.3f F%.0f\n", v.x(), v.y(), feed));
            }
            if (c.isClosed()) {
                sb.append(String.format(Locale.US, "G1 X%.3f Y%.3f F%.0f\n", first.x(), first.y(), feed));
            }
        }
        sb.append("G0 Z15\n");
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

    private double[] bboxMin(GeometryModel model) {
        double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY;
        for (Contour c : model.getContours()) {
            for (Vec2 p : c.getPoints()) {
                minX = Math.min(minX, p.x());
                minY = Math.min(minY, p.y());
            }
        }
        if (!Double.isFinite(minX)) return new double[]{0, 0};
        return new double[]{minX, minY};
    }
}
