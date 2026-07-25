package com.timbernest.machinability;

import com.timbernest.admin.Tool;
import com.timbernest.geometry.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class DogBoneService {
    private static final Logger log = LoggerFactory.getLogger(DogBoneService.class);

    public int apply(GeometryModel model, Tool tool, double scale) {
        double r = tool.getDiameterMm() / 2.0 * Math.max(0.5, Math.min(scale, 1.5));
        int added = 0;
        for (Contour c : model.getContours()) {
            if (!c.isClosed() || c.getPoints().size() < 3) continue;
            List<Vec2> pts = c.getPoints();
            List<Vec2> out = new ArrayList<>();
            for (int i = 0; i < pts.size(); i++) {
                Vec2 prev = pts.get((i - 1 + pts.size()) % pts.size());
                Vec2 cur = pts.get(i);
                Vec2 next = pts.get((i + 1) % pts.size());
                out.add(cur);
                double cross = (cur.x() - prev.x()) * (next.y() - cur.y())
                        - (cur.y() - prev.y()) * (next.x() - cur.x());
                if (cross < -1e-3) {
                    Vec2 bis = bisector(prev, cur, next);
                    out.add(new Vec2(cur.x() + bis.x() * r, cur.y() + bis.y() * r));
                    added++;
                }
            }
            c.setPoints(out);
        }
        log.info("Applied dog-bones: {} corners, radius={}", added, r);
        return added;
    }

    private Vec2 bisector(Vec2 a, Vec2 b, Vec2 c) {
        double ax = a.x() - b.x(), ay = a.y() - b.y();
        double cx = c.x() - b.x(), cy = c.y() - b.y();
        double al = Math.hypot(ax, ay), cl = Math.hypot(cx, cy);
        if (al < 1e-9 || cl < 1e-9) return new Vec2(0, 0);
        ax /= al; ay /= al; cx /= cl; cy /= cl;
        double bx = -(ax + cx), by = -(ay + cy);
        double bl = Math.hypot(bx, by);
        if (bl < 1e-9) return new Vec2(0, 0);
        return new Vec2(bx / bl, by / bl);
    }
}
