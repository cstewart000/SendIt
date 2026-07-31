package com.timbernest.geometry;

import com.timbernest.geometry.model.Contour;
import com.timbernest.geometry.model.GeometryModel;
import com.timbernest.geometry.model.Vec2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/** Greedy join of open contours that share endpoints within eps. */
public final class ContourJoiner {
    private static final Logger log = LoggerFactory.getLogger(ContourJoiner.class);

    private ContourJoiner() {}

    public static int join(GeometryModel model, double eps) {
        List<Contour> open = new ArrayList<>();
        List<Contour> closed = new ArrayList<>();
        for (Contour c : model.getContours()) {
            if (c.isClosed() || c.getPoints().size() < 2) closed.add(c);
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
                    open.set(i, merged);
                    open.remove(j);
                    merges++;
                    changed = true;
                    break outer;
                }
            }
        }
        for (Contour c : open) {
            Vec2 a = c.getPoints().get(0), b = c.getPoints().get(c.getPoints().size() - 1);
            if (a.dist(b) <= eps * 2) {
                c.getPoints().set(c.getPoints().size() - 1, a);
                c.setClosed(true);
            }
        }
        List<Contour> all = new ArrayList<>(closed);
        all.addAll(open);
        model.setContours(all);
        log.info("Joined {} open contour merges; open left={}", merges, open.size());
        return merges;
    }

    private static Contour tryMerge(Contour a, Contour b, double eps) {
        List<Vec2> ap = a.getPoints(), bp = b.getPoints();
        Vec2 a0 = ap.get(0), a1 = ap.get(ap.size() - 1);
        Vec2 b0 = bp.get(0), b1 = bp.get(bp.size() - 1);
        Contour out = new Contour();
        out.setId(a.getId());
        out.setLayer(a.getLayer());
        List<Vec2> pts = new ArrayList<>();
        if (a1.dist(b0) <= eps) {
            pts.addAll(ap); pts.addAll(bp.subList(1, bp.size()));
        } else if (a1.dist(b1) <= eps) {
            pts.addAll(ap);
            for (int i = bp.size() - 2; i >= 0; i--) pts.add(bp.get(i));
        } else if (a0.dist(b1) <= eps) {
            pts.addAll(bp); pts.addAll(ap.subList(1, ap.size()));
        } else if (a0.dist(b0) <= eps) {
            for (int i = bp.size() - 1; i >= 0; i--) pts.add(bp.get(i));
            pts.addAll(ap.subList(1, ap.size()));
        } else return null;
        out.setPoints(pts);
        return out;
    }
}
