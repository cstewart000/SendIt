package com.timbernest.geometry;

import com.timbernest.geometry.model.Contour;
import com.timbernest.geometry.model.GeometryModel;
import com.timbernest.geometry.model.Vec2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Component
public class PartExtractor {
    private static final Logger log = LoggerFactory.getLogger(PartExtractor.class);

    public record ExtractedPart(String contourId, String label, GeometryModel geometry,
                                double widthMm, double heightMm) {}

    public List<ExtractedPart> extract(GeometryModel model) {
        List<Contour> closed = model.getContours().stream()
                .filter(c -> c.isClosed() && c.getPoints().size() >= 3)
                .sorted(Comparator.comparingDouble(this::area).reversed())
                .toList();
        boolean[] isHole = new boolean[closed.size()];
        int[] parent = new int[closed.size()];
        for (int i = 0; i < closed.size(); i++) parent[i] = -1;

        for (int i = 0; i < closed.size(); i++) {
            for (int j = 0; j < closed.size(); j++) {
                if (i == j) continue;
                if (contains(closed.get(j), closed.get(i)) && area(closed.get(j)) > area(closed.get(i))) {
                    if (parent[i] < 0 || area(closed.get(j)) < area(closed.get(parent[i]))) {
                        parent[i] = j;
                        isHole[i] = true;
                    }
                }
            }
        }

        List<ExtractedPart> parts = new ArrayList<>();
        int idx = 0;
        for (int i = 0; i < closed.size(); i++) {
            if (isHole[i]) continue;
            Contour outer = closed.get(i);
            GeometryModel g = new GeometryModel();
            g.setUnits(model.getUnits());
            g.getContours().add(copy(outer));
            for (int h = 0; h < closed.size(); h++) {
                if (parent[h] == i) g.getContours().add(copy(closed.get(h)));
            }
            double[] b = outer.bbox();
            String label = "Part " + (++idx);
            parts.add(new ExtractedPart(outer.getId(), label, g,
                    Math.max(1, b[2] - b[0]), Math.max(1, b[3] - b[1])));
        }
        log.info("Extracted {} nestable parts from {} closed contours", parts.size(), closed.size());
        return parts;
    }

    private Contour copy(Contour c) {
        Contour n = new Contour();
        n.setId(c.getId());
        n.setLayer(c.getLayer());
        n.setClosed(c.isClosed());
        n.setPoints(new ArrayList<>(c.getPoints()));
        return n;
    }

    private double area(Contour c) {
        double[] b = c.bbox();
        return Math.max(0, (b[2] - b[0]) * (b[3] - b[1]));
    }

    private boolean contains(Contour outer, Contour inner) {
        double[] ob = outer.bbox(), ib = inner.bbox();
        if (ib[0] < ob[0] - 0.01 || ib[1] < ob[1] - 0.01
                || ib[2] > ob[2] + 0.01 || ib[3] > ob[3] + 0.01) return false;
        Vec2 mid = new Vec2((ib[0] + ib[2]) / 2, (ib[1] + ib[3]) / 2);
        return pointInPoly(mid, outer.getPoints());
    }

    private boolean pointInPoly(Vec2 p, List<Vec2> poly) {
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
