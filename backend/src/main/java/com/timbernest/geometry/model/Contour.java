package com.timbernest.geometry.model;

import java.util.ArrayList;
import java.util.List;

public class Contour {
    private String id;
    private String layer = "0";
    private boolean closed;
    private List<Vec2> points = new ArrayList<>();

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getLayer() { return layer; }
    public void setLayer(String layer) { this.layer = layer; }
    public boolean isClosed() { return closed; }
    public void setClosed(boolean closed) { this.closed = closed; }
    public List<Vec2> getPoints() { return points; }
    public void setPoints(List<Vec2> points) { this.points = points; }

    public double pathLength() {
        double len = 0;
        for (int i = 1; i < points.size(); i++) len += points.get(i - 1).dist(points.get(i));
        if (closed && points.size() > 1) len += points.get(points.size() - 1).dist(points.get(0));
        return len;
    }

    public double[] bbox() {
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for (Vec2 p : points) {
            minX = Math.min(minX, p.x()); minY = Math.min(minY, p.y());
            maxX = Math.max(maxX, p.x()); maxY = Math.max(maxY, p.y());
        }
        return new double[]{minX, minY, maxX, maxY};
    }
}
