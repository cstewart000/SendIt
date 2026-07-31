package com.timbernest.geometry;

import com.timbernest.common.ApiException;
import com.timbernest.geometry.model.Contour;
import com.timbernest.geometry.model.GeometryModel;
import com.timbernest.geometry.model.Vec2;
import io.dwg.api.DwgDocument;
import io.dwg.api.DwgReader;
import io.dwg.core.type.Point2D;
import io.dwg.core.type.Point3D;
import io.dwg.entities.DwgEntity;
import io.dwg.entities.concrete.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Component
public class DwgParser {
    private static final Logger log = LoggerFactory.getLogger(DwgParser.class);
    private static final double LIM = 1e6;
    private static final double MAX_R = 5e4;
    private static final double MAX_SEG = 5e4;

    public GeometryModel parse(Path file) {
        try {
            DwgDocument doc = DwgReader.defaultReader().open(file);
            GeometryModel model = new GeometryModel();
            int seq = 0, skipped = 0;
            for (DwgEntity e : doc.entities()) {
                Contour c = map(e);
                if (c == null || c.getPoints().isEmpty()) { skipped++; continue; }
                String pfx = e instanceof DwgLine ? "L" : e instanceof DwgCircle ? "C"
                        : e instanceof DwgArc ? "A" : e instanceof DwgEllipse ? "E" : "P";
                c.setId(pfx + (++seq));
                c.setLayer("0");
                model.getContours().add(c);
            }
            DwgCleaner.clean(model);
            log.info("DWG parsed: version={} contours={} skipped={} from {}",
                    doc.version(), model.getContours().size(), skipped, file.getFileName());
            if (model.getContours().isEmpty()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "No usable 2D geometry found in DWG");
            }
            return model;
        } catch (ApiException ex) { throw ex; }
        catch (Exception e) {
            log.error("DWG parse failed: {}", e.toString());
            throw new ApiException(HttpStatus.BAD_REQUEST, "Failed to parse DWG: " + e.getMessage());
        }
    }

    private Contour map(DwgEntity e) {
        if (e instanceof DwgLine line) return line(line);
        if (e instanceof DwgCircle cir) return circle(cir);
        if (e instanceof DwgArc arc) return arc(arc);
        if (e instanceof DwgEllipse el) return ellipse(el);
        if (e instanceof DwgLwPolyline lw) return from2d(lw.vertices(), lw.isClosed());
        if (e instanceof DwgPolyline2D p) {
            List<Vec2> pts = new ArrayList<>();
            for (Point3D v : p.vertices()) if (v != null && ok(v)) pts.add(xy(v));
            return poly(pts, p.isClosed());
        }
        return null;
    }

    private Contour line(DwgLine line) {
        if (line.start() == null || line.end() == null || !ok(line.start()) || !ok(line.end())) return null;
        double len = Math.hypot(line.end().x() - line.start().x(), line.end().y() - line.start().y());
        if (len < 1e-6 || len > MAX_SEG) return null;
        Contour c = new Contour();
        c.getPoints().add(xy(line.start()));
        c.getPoints().add(xy(line.end()));
        return c;
    }

    private Contour circle(DwgCircle cir) {
        if (cir.center() == null || !ok(cir.center()) || cir.radius() <= 0 || cir.radius() > MAX_R) return null;
        return ArcTessellator.circle(cir.center().x(), cir.center().y(), cir.radius());
    }

    private Contour arc(DwgArc arc) {
        if (arc.center() == null || !ok(arc.center()) || arc.radius() <= 0 || arc.radius() > MAX_R) return null;
        return ArcTessellator.arc(arc.center().x(), arc.center().y(), arc.radius(),
                arc.startAngle(), arc.endAngle(), false);
    }

    private Contour ellipse(DwgEllipse el) {
        if (el.center() == null || el.majorAxisVec() == null || !ok(el.center())) return null;
        if (el.axisRatio() < 0.05 || el.majorRadius() <= 0 || el.majorRadius() > MAX_R) return null;
        double cx = el.center().x(), cy = el.center().y();
        double mx = el.majorAxisVec().x(), my = el.majorAxisVec().y();
        double maj = el.majorRadius(), ang = Math.atan2(my, mx), ratio = el.axisRatio();
        double a0 = el.startParam(), a1 = el.endParam();
        if (!(Double.isFinite(a0) && Double.isFinite(a1)) || Math.abs(a1 - a0) < 1e-9) {
            a0 = 0; a1 = Math.PI * 2;
        }
        double span = a1 - a0;
        if (span < 0) span += Math.PI * 2;
        Contour c = new Contour();
        c.setClosed(span >= Math.PI * 2 - 0.05);
        int steps = 48;
        for (int i = 0; i <= steps; i++) {
            double t = a0 + span * i / steps;
            double x = cx + maj * Math.cos(t) * Math.cos(ang) - maj * ratio * Math.sin(t) * Math.sin(ang);
            double y = cy + maj * Math.cos(t) * Math.sin(ang) + maj * ratio * Math.sin(t) * Math.cos(ang);
            if (ok(x, y)) c.getPoints().add(new Vec2(x, y));
        }
        return c.getPoints().size() < 3 ? null : c;
    }

    private Contour from2d(List<Point2D> verts, boolean closed) {
        List<Vec2> pts = new ArrayList<>();
        for (Point2D p : verts) if (p != null && ok(p.x(), p.y())) pts.add(new Vec2(p.x(), p.y()));
        return poly(pts, closed);
    }

    private Contour poly(List<Vec2> pts, boolean closed) {
        if (pts.size() < 2) return null;
        Contour c = new Contour();
        c.setClosed(closed);
        c.setPoints(pts);
        return c.pathLength() > MAX_SEG * 4 ? null : c;
    }

    private static Vec2 xy(Point3D p) { return new Vec2(p.x(), p.y()); }
    private static boolean ok(Point3D p) { return ok(p.x(), p.y()); }
    private static boolean ok(double x, double y) {
        return Double.isFinite(x) && Double.isFinite(y) && Math.abs(x) < LIM && Math.abs(y) < LIM;
    }
}
