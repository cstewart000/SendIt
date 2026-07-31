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
            // Join LINE/ARC chains into closed profiles (critical for Voron-style DXFs)
            ContourJoiner.joinAdaptive(model);
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
            } else if ("LWPOLYLINE".equalsIgnoreCase(val)) {
                Contour c = readLwPolyline(lines, i);
                if (c != null) { c.setId("P" + (++seq)); model.getContours().add(c); }
            } else if ("POLYLINE".equalsIgnoreCase(val)) {
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

    /**
     * LWPOLYLINE with optional per-vertex bulge (group 42) for arc segments.
     * Without bulges, curved corners become sharp chords and joins can fail.
     */
    private Contour readLwPolyline(List<String> lines, int start) {
        boolean closed = false;
        String layer = "0";
        List<double[]> verts = new ArrayList<>(); // x, y, bulge
        Double x = null;
        boolean hasX = false;

        for (int i = start; i < lines.size() - 1; i += 2) {
            int code = parseInt(lines.get(i).trim());
            String val = lines.get(i + 1).trim();
            if (code == 0) break;
            if (code == 70) closed = (parseInt(val) & 1) == 1;
            if (code == 8) layer = val;
            if (code == 10) {
                x = parseD(val);
                hasX = true;
            } else if (code == 20 && hasX) {
                verts.add(new double[]{x, parseD(val), 0});
                hasX = false;
                // bulge may arrive after this vertex as code 42
            } else if (code == 42 && !verts.isEmpty()) {
                verts.get(verts.size() - 1)[2] = parseD(val);
            }
        }
        if (verts.isEmpty()) return null;

        Contour c = new Contour();
        c.setLayer(layer);
        c.setClosed(closed);
        List<Vec2> pts = new ArrayList<>();
        int n = verts.size();
        int segs = closed ? n : n - 1;
        for (int i = 0; i < segs; i++) {
            double[] a = verts.get(i);
            double[] b = verts.get((i + 1) % n);
            Vec2 p0 = new Vec2(a[0], a[1]);
            Vec2 p1 = new Vec2(b[0], b[1]);
            if (pts.isEmpty()) pts.add(p0);
            appendBulge(pts, p0, p1, a[2]);
        }
        if (!closed && n > 0) {
            // last vertex already added via final segment end
        }
        c.setPoints(pts);
        return pts.size() < 2 ? null : c;
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
        Contour c = ArcTessellator.circle(n.get(10), n.getOrDefault(20, 0.0), n.get(40));
        c.setClosed(true);
        return c;
    }

    private Contour readArc(List<String> lines, int start) {
        Map<Integer, Double> n = nums(lines, start);
        if (!n.containsKey(10) || !n.containsKey(40)) return null;
        double a0 = n.getOrDefault(50, 0.0);
        double a1 = n.getOrDefault(51, 360.0);
        // DXF ARC is always CCW from start→end; wrap when end ≤ start
        Contour c = arcDegrees(n.get(10), n.getOrDefault(20, 0.0), n.get(40), a0, a1);
        c.setClosed(false);
        return c;
    }

    /**
     * Tessellate DXF ARC (angles in degrees, CCW). Endpoints match exact start/end
     * so ContourJoiner can chain to LINE entities.
     */
    static Contour arcDegrees(double cx, double cy, double r, double a0Deg, double a1Deg) {
        double a0 = a0Deg;
        double a1 = a1Deg;
        while (a1 <= a0) a1 += 360.0;
        double span = a1 - a0;
        int steps = Math.max(8, (int) Math.ceil(span / 6.0)); // denser near curves for join fidelity
        Contour c = new Contour();
        c.setClosed(false);
        for (int i = 0; i <= steps; i++) {
            double a = Math.toRadians(a0 + span * i / steps);
            c.getPoints().add(new Vec2(cx + r * Math.cos(a), cy + r * Math.sin(a)));
        }
        return c;
    }

    /**
     * Append points along bulge arc from p0→p1 (p0 already in list).
     * Bulge = tan(includedAngle/4); positive = CCW (DXF convention).
     */
    static void appendBulge(List<Vec2> pts, Vec2 p0, Vec2 p1, double bulge) {
        if (Math.abs(bulge) < 1e-12) {
            pts.add(p1);
            return;
        }
        double dx = p1.x() - p0.x(), dy = p1.y() - p0.y();
        double chord = Math.hypot(dx, dy);
        if (chord < 1e-12) {
            pts.add(p1);
            return;
        }
        double angle = 4.0 * Math.atan(bulge); // signed included angle
        double absAngle = Math.abs(angle);
        if (absAngle < 1e-12 || absAngle > Math.PI * 2 - 1e-9) {
            pts.add(p1);
            return;
        }
        double radius = chord / (2.0 * Math.sin(absAngle / 2.0));
        double mx = (p0.x() + p1.x()) / 2.0;
        double my = (p0.y() + p1.y()) / 2.0;
        // Distance from midpoint to center (signed via bulge)
        double dist = chord * (1.0 - bulge * bulge) / (4.0 * bulge);
        // Left normal of chord direction (dx,dy): (-dy, dx)
        double inv = 1.0 / chord;
        double cx = mx - dy * inv * dist;
        double cy = my + dx * inv * dist;

        // Walk the signed included angle from start (bulge>0 = CCW)
        double a0 = Math.atan2(p0.y() - cy, p0.x() - cx);
        int steps = Math.max(6, (int) Math.ceil(absAngle / (Math.PI / 18)));
        for (int i = 1; i <= steps; i++) {
            if (i == steps) {
                pts.add(p1); // exact end vertex
            } else {
                double a = a0 + angle * i / steps;
                pts.add(new Vec2(cx + radius * Math.cos(a), cy + radius * Math.sin(a)));
            }
        }
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
