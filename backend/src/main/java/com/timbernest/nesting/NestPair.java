package com.timbernest.nesting;

import com.timbernest.geometry.model.Vec2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/** Complementary 0°/180° pair layout for denser nesting (e.g. T one-up/one-down). */
public final class NestPair {
    private static final Logger log = LoggerFactory.getLogger(NestPair.class);
    private NestPair() {}

    public record Pose(double x, double y, double rot) {}

    public record Layout(Pose a, Pose b, double width, double height, double area) {}

    public static Layout bestLayout(List<Vec2> local, double nw, double nh, double gap) {
        double[] box180 = NestMath.aabb(nw, nh, 180);
        double bw = box180[0], bh = box180[1];
        double sideBySide = (nw + gap + bw) * Math.max(nh, bh);
        Layout best = new Layout(new Pose(0, 0, 0), new Pose(nw + gap, 0, 180),
                nw + gap + bw, Math.max(nh, bh), sideBySide);
        if (local.size() < 3) return best;
        double step = Math.max(4, Math.min(nw, nh) / 36);
        for (double sy = -nh; sy <= nh + step; sy += step) {
            for (double sx = -nw * 0.25; sx <= nw * 1.6; sx += step) {
                List<Vec2> a = NestPoly.world(local, nw, nh, 0, 0, 0);
                List<Vec2> b = NestPoly.world(local, nw, nh, sx, sy, 180);
                if (NestPoly.collide(a, b, gap)) continue;
                // Packing box = AABB union (AABBs may overlap when shapes interlock).
                double minX = Math.min(0, sx), minY = Math.min(0, sy);
                double maxX = Math.max(nw, sx + bw), maxY = Math.max(nh, sy + bh);
                double w = maxX - minX, h = maxY - minY, area = w * h;
                if (area + 0.5 < best.area()) {
                    best = new Layout(
                            new Pose(-minX, -minY, 0),
                            new Pose(sx - minX, sy - minY, 180),
                            w, h, area);
                }
            }
        }
        log.info("Pair layout {}x{} area={} (side-by-side {})",
                Math.round(best.width()), Math.round(best.height()),
                Math.round(best.area()), Math.round(sideBySide));
        return best;
    }
}
