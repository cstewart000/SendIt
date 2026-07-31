package com.timbernest.nesting;

import java.util.List;

/** Rank packs by area × aspect so nests stay dense and grow in 2D. */
public final class NestScore {
    private NestScore() {}

    public record Box(double minX, double minY, double maxX, double maxY) {
        double w() { return maxX - minX; }
        double h() { return maxY - minY; }
        double area() { return w() * h(); }
        double aspect() {
            double a = Math.max(w(), h()), b = Math.max(1e-6, Math.min(w(), h()));
            return a / b;
        }
    }

    public static Box of(List<NestBlf.Placed> placed) {
        if (placed.isEmpty()) return new Box(0, 0, 0, 0);
        double minX = placed.get(0).x0(), minY = placed.get(0).y0();
        double maxX = placed.get(0).x1(), maxY = placed.get(0).y1();
        for (NestBlf.Placed pl : placed) {
            minX = Math.min(minX, pl.x0());
            minY = Math.min(minY, pl.y0());
            maxX = Math.max(maxX, pl.x1());
            maxY = Math.max(maxY, pl.y1());
        }
        return new Box(minX, minY, maxX, maxY);
    }

    public static Box union(List<NestBlf.Placed> placed, double x, double y, double w, double h) {
        double minX = x, minY = y, maxX = x + w, maxY = y + h;
        for (NestBlf.Placed pl : placed) {
            minX = Math.min(minX, pl.x0());
            minY = Math.min(minY, pl.y0());
            maxX = Math.max(maxX, pl.x1());
            maxY = Math.max(maxY, pl.y1());
        }
        return new Box(minX, minY, maxX, maxY);
    }

    /** Lower is better: dense area, mild aspect penalty, then bottom-left. */
    public static double rank(Box box, double x, double y) {
        return box.area() * box.aspect() + y * 10 + x;
    }
}
