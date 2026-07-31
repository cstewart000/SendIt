package com.timbernest.geometry;

import com.timbernest.geometry.model.Contour;
import com.timbernest.geometry.model.Vec2;

/** Tessellate arc/circle into polyline contours (angles in radians). */
public final class ArcTessellator {
    private ArcTessellator() {}

    public static Contour circle(double cx, double cy, double r) {
        return arc(cx, cy, r, 0, Math.PI * 2, true);
    }

    public static Contour arc(double cx, double cy, double r, double a0, double a1, boolean closed) {
        Contour c = new Contour();
        c.setClosed(closed);
        double span = closed ? Math.PI * 2 : (a1 - a0);
        if (!closed && span < 0) span += Math.PI * 2;
        int steps = closed ? 32 : Math.max(8, (int) Math.ceil(Math.abs(span) / (Math.PI / 18)));
        for (int i = 0; i <= steps; i++) {
            double a = a0 + span * i / steps;
            c.getPoints().add(new Vec2(cx + r * Math.cos(a), cy + r * Math.sin(a)));
        }
        return c;
    }
}
