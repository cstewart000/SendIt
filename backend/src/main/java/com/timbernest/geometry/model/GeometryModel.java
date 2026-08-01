package com.timbernest.geometry.model;

import java.util.ArrayList;
import java.util.List;

public class GeometryModel {
    private String units = "mm";
    private List<Contour> contours = new ArrayList<>();
    private List<String> purgedLayers = new ArrayList<>();

    public String getUnits() { return units; }
    public void setUnits(String units) { this.units = units; }
    public List<Contour> getContours() { return contours; }
    public void setContours(List<Contour> contours) { this.contours = contours; }
    public List<String> getPurgedLayers() { return purgedLayers; }
    public void setPurgedLayers(List<String> purgedLayers) {
        this.purgedLayers = purgedLayers != null ? purgedLayers : new java.util.ArrayList<>();
    }

    public double[] bbox() {
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for (Contour c : contours) {
            double[] b = c.bbox();
            minX = Math.min(minX, b[0]); minY = Math.min(minY, b[1]);
            maxX = Math.max(maxX, b[2]); maxY = Math.max(maxY, b[3]);
        }
        if (contours.isEmpty()) return new double[]{0, 0, 0, 0};
        return new double[]{minX, minY, maxX, maxY};
    }

    public double width() { double[] b = bbox(); return b[2] - b[0]; }
    public double height() { double[] b = bbox(); return b[3] - b[1]; }
}
