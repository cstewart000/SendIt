package com.timbernest.geometry;

import com.timbernest.common.ApiException;
import com.timbernest.geometry.model.Contour;
import com.timbernest.geometry.model.GeometryModel;
import com.timbernest.geometry.model.Vec2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

@Component
public class DxfParser {
    private static final Logger log = LoggerFactory.getLogger(DxfParser.class);

    public GeometryModel parse(Path file) {
        try {
            List<String> lines = Files.readAllLines(file);
            GeometryModel model = new GeometryModel();
            parseEntities(lines, model);
            log.info("DXF parsed: {} contours from {}", model.getContours().size(), file.getFileName());
            return model;
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Failed to parse DXF: " + e.getMessage());
        }
    }

    private void parseEntities(List<String> lines, GeometryModel model) {
        int i = 0, seq = 0;
        while (i < lines.size() - 1) {
            String code = lines.get(i).trim();
            String val = lines.get(i + 1).trim();
            i += 2;
            if (!"0".equals(code)) continue;
            if ("LINE".equalsIgnoreCase(val)) {
                Contour c = readLine(lines, i);
                if (c != null) { c.setId("L" + (++seq)); model.getContours().add(c); }
            } else if ("LWPOLYLINE".equalsIgnoreCase(val) || "POLYLINE".equalsIgnoreCase(val)) {
                Contour c = readPolyline(lines, i);
                if (c != null) { c.setId("P" + (++seq)); model.getContours().add(c); }
            } else if ("CIRCLE".equalsIgnoreCase(val)) {
                Contour c = readCircle(lines, i);
                if (c != null) { c.setId("C" + (++seq)); model.getContours().add(c); }
            } else if ("ARC".equalsIgnoreCase(val)) {
                Contour c = readArc(lines, i);
                if (c != null) { c.setId("A" + (++seq)); model.getContours().add(c); }
            }
        }
        if (model.getContours().isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "No usable 2D geometry found in DXF");
        }
    }

    private Contour readLine(List<String> lines, int start) {
        Map<Integer, Double> n = nums(lines, start);
        if (!n.containsKey(10) || !n.containsKey(11)) return null;
        Contour c = new Contour();
        c.getPoints().add(new Vec2(n.get(10), n.getOrDefault(20, 0.0)));
        c.getPoints().add(new Vec2(n.get(11), n.getOrDefault(21, 0.0)));
        c.setClosed(false);
        return c;
    }

    private Contour readPolyline(List<String> lines, int start) {
        Contour c = new Contour();
        boolean closed = false;
        Double x = null;
        for (int i = start; i < lines.size() - 1; i += 2) {
            int code = parseInt(lines.get(i).trim());
            String val = lines.get(i + 1).trim();
            if (code == 0) break;
            if (code == 70) closed = (parseInt(val) & 1) == 1;
            if (code == 8) c.setLayer(val);
            if (code == 10) x = parseD(val);
            if (code == 20 && x != null) {
                c.getPoints().add(new Vec2(x, parseD(val)));
                x = null;
            }
        }
        c.setClosed(closed);
        return c.getPoints().isEmpty() ? null : c;
    }

    private Contour readCircle(List<String> lines, int start) {
        Map<Integer, Double> n = nums(lines, start);
        if (!n.containsKey(10) || !n.containsKey(40)) return null;
        return arcPoints(n.get(10), n.getOrDefault(20, 0.0), n.get(40), 0, 360, true);
    }

    private Contour readArc(List<String> lines, int start) {
        Map<Integer, Double> n = nums(lines, start);
        if (!n.containsKey(10) || !n.containsKey(40)) return null;
        return arcPoints(n.get(10), n.getOrDefault(20, 0.0), n.get(40),
                n.getOrDefault(50, 0.0), n.getOrDefault(51, 360.0), false);
    }

    private Contour arcPoints(double cx, double cy, double r, double a0, double a1, boolean closed) {
        Contour c = new Contour();
        c.setClosed(closed);
        int steps = closed ? 32 : Math.max(8, (int) Math.ceil(Math.abs(a1 - a0) / 10));
        double span = closed ? 360 : (a1 - a0);
        for (int i = 0; i <= steps; i++) {
            double a = Math.toRadians(a0 + span * i / steps);
            c.getPoints().add(new Vec2(cx + r * Math.cos(a), cy + r * Math.sin(a)));
        }
        return c;
    }

    private Map<Integer, Double> nums(List<String> lines, int start) {
        Map<Integer, Double> m = new HashMap<>();
        for (int i = start; i < lines.size() - 1; i += 2) {
            int code = parseInt(lines.get(i).trim());
            String val = lines.get(i + 1).trim();
            if (code == 0) break;
            if (code == 8) continue;
            try { m.put(code, parseD(val)); } catch (Exception ignored) {}
        }
        return m;
    }

    private int parseInt(String s) { try { return Integer.parseInt(s.trim()); } catch (Exception e) { return -1; } }
    private double parseD(String s) { return Double.parseDouble(s.trim()); }
}
