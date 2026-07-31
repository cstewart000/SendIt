package com.timbernest.geometry;

import com.timbernest.geometry.model.Contour;
import com.timbernest.geometry.model.GeometryModel;
import com.timbernest.geometry.model.Vec2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/** Drop garbage DWG entities using circle-anchored bbox + length limits. */
public final class DwgCleaner {
    private static final Logger log = LoggerFactory.getLogger(DwgCleaner.class);

    private DwgCleaner() {}

    public static void clean(GeometryModel model) {
        List<Contour> circles = model.getContours().stream()
                .filter(c -> c.isClosed() && c.getId() != null && c.getId().startsWith("C"))
                .toList();
        double[] box = circles.isEmpty() ? robustBox(model.getContours()) : bbox(circles);
        if (box == null) return;
        double diag = Math.hypot(box[2] - box[0], box[3] - box[1]);
        double pad = Math.max(diag * 0.35, 50);
        double maxSeg = Math.max(diag * 1.5, 500);
        double minX = box[0] - pad, minY = box[1] - pad, maxX = box[2] + pad, maxY = box[3] + pad;

        List<Contour> kept = new ArrayList<>();
        int dropped = 0;
        for (Contour c : model.getContours()) {
            if (!inside(c, minX, minY, maxX, maxY) || c.pathLength() > maxSeg * 4) {
                dropped++;
                continue;
            }
            if (!c.isClosed() && c.pathLength() > maxSeg) { dropped++; continue; }
            kept.add(c);
        }
        model.setContours(kept);
        double eps = Math.max(0.5, diag * 0.002);
        ContourJoiner.join(model, eps);
        log.info("DWG clean dropped={} kept={} eps={} box=[{},{}]-[{},{}]",
                dropped, model.getContours().size(), Math.round(eps),
                Math.round(minX), Math.round(minY), Math.round(maxX), Math.round(maxY));
    }

    private static boolean inside(Contour c, double minX, double minY, double maxX, double maxY) {
        for (Vec2 p : c.getPoints()) {
            if (p.x() < minX || p.y() < minY || p.x() > maxX || p.y() > maxY) return false;
        }
        return !c.getPoints().isEmpty();
    }

    private static double[] bbox(List<Contour> contours) {
        double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;
        for (Contour c : contours) {
            double[] b = c.bbox();
            minX = Math.min(minX, b[0]); minY = Math.min(minY, b[1]);
            maxX = Math.max(maxX, b[2]); maxY = Math.max(maxY, b[3]);
        }
        if (!Double.isFinite(minX)) return null;
        return new double[]{minX, minY, maxX, maxY};
    }

    private static double[] robustBox(List<Contour> contours) {
        List<Double> xs = new ArrayList<>(), ys = new ArrayList<>();
        for (Contour c : contours) for (Vec2 p : c.getPoints()) { xs.add(p.x()); ys.add(p.y()); }
        if (xs.size() < 2) return null;
        xs.sort(Double::compare); ys.sort(Double::compare);
        int lo = xs.size() / 20, hi = xs.size() - 1 - lo;
        return new double[]{xs.get(lo), ys.get(lo), xs.get(hi), ys.get(hi)};
    }
}
