package com.timbernest.geometry.model;

public record Vec2(double x, double y) {
    public double dist(Vec2 o) {
        double dx = x - o.x, dy = y - o.y;
        return Math.hypot(dx, dy);
    }
}
