package com.timbernest.machinability;

import com.timbernest.admin.Tool;
import com.timbernest.geometry.model.Contour;
import com.timbernest.geometry.model.GeometryModel;
import com.timbernest.geometry.model.Vec2;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DogBoneServiceTest {

    private final DogBoneService svc = new DogBoneService();

    @Test
    void dogboneIntoMaterialPreservesStraightEdges() {
        GeometryModel m = lShape();
        Contour c = m.getContours().get(0);

        assertTrue(svc.countCandidates(m) >= 1);
        DogBoneService.Result r = svc.apply(m, tool(6), 1.0);
        assertTrue(r.corners() >= 1);
        assertEquals(3.0, r.radiusMm(), 0.01);

        List<Vec2> pts = c.getPoints();
        // Arc samples present (270° lobe needs many points)
        assertTrue(pts.size() > 12, "size=" + pts.size());

        // Into solid L (x<40 and y<40 near re-entrant corner)
        assertTrue(pts.stream().anyMatch(p -> p.x() < 39 && p.y() < 39),
                "dog-bone must cut into part material");
        // Not into free space of L (the missing square)
        assertFalse(pts.stream().anyMatch(p -> p.x() > 42 && p.y() > 42),
                "must not bulge into free space");

        // Original non-corner vertices still present (outer edges intact)
        assertTrue(containsNear(pts, new Vec2(0, 0), 0.05));
        assertTrue(containsNear(pts, new Vec2(100, 0), 0.05));
        assertTrue(containsNear(pts, new Vec2(100, 40), 0.05));
        assertTrue(containsNear(pts, new Vec2(40, 100), 0.05));
        assertTrue(containsNear(pts, new Vec2(0, 100), 0.05));

        // Horizontal arm edge stays on y=40 for x>43 (beyond dog-bone setback)
        assertTrue(pts.stream().anyMatch(p -> Math.abs(p.y() - 40) < 0.05 && p.x() > 45),
                "horizontal edge beyond dog-bone must stay on y=40");
        // Vertical arm edge stays on x=40 for y>43
        assertTrue(pts.stream().anyMatch(p -> Math.abs(p.x() - 40) < 0.05 && p.y() > 45),
                "vertical edge beyond dog-bone must stay on x=40");

        // No original edge points bent off their lines (beyond setback zone)
        for (Vec2 p : pts) {
            if (Math.abs(p.y()) < 0.05) assertTrue(p.x() >= -0.05 && p.x() <= 100.05);
            if (Math.abs(p.x() - 100) < 0.05) assertTrue(p.y() >= -0.05 && p.y() <= 40.05);
            if (Math.abs(p.x()) < 0.05) assertTrue(p.y() >= -0.05 && p.y() <= 100.05);
            if (Math.abs(p.y() - 100) < 0.05) assertTrue(p.x() >= -0.05 && p.x() <= 40.05);
        }
    }

    @Test
    void arcEndpointsLieOnOriginalEdgesAndRadiusMatchesTool() {
        List<Vec2> poly = List.of(
                new Vec2(0, 0), new Vec2(100, 0), new Vec2(100, 40),
                new Vec2(40, 40), new Vec2(40, 100), new Vec2(0, 100));
        Vec2 a = new Vec2(100, 40), b = new Vec2(40, 40), c = new Vec2(40, 100);
        double r = 3.0;
        List<Vec2> arc = svc.dogboneArc(a, b, c, r, poly, false);
        assertTrue(arc.size() >= 12, "samples=" + arc.size());

        Vec2 t1 = arc.get(0), t2 = arc.get(arc.size() - 1);
        // On horizontal edge y=40, setback r from corner
        assertEquals(40.0, t1.y(), 1e-9);
        assertEquals(40.0 + r, t1.x(), 1e-6);
        // On vertical edge x=40, setback r from corner
        assertEquals(40.0, t2.x(), 1e-9);
        assertEquals(40.0 + r, t2.y(), 1e-6);

        // Classic dog-bone: every sample is at distance r from corner B
        for (Vec2 p : arc) {
            assertEquals(r, p.dist(b), 1e-5, "point " + p + " not on circle around corner");
        }

        // Curve (major arc) is much longer than the short free-space chord
        double chord = t1.dist(t2);
        double path = 0;
        for (int i = 1; i < arc.size(); i++) path += arc.get(i - 1).dist(arc.get(i));
        assertTrue(path > chord * 2.0,
                "classic dog-bone is the long arc into material; path=" + path + " chord=" + chord);

        // Mid sample deep in solid, not free space
        Vec2 mid = arc.get(arc.size() / 2);
        assertTrue(mid.x() < 40 && mid.y() < 40, "mid=" + mid);
        assertEquals(r, mid.dist(b), 1e-5);
    }

    @Test
    void originalStraightSegmentsStayCollinear() {
        GeometryModel m = lShape();
        Contour c = m.getContours().get(0);
        svc.apply(m, tool(6), 1.0);
        List<Vec2> pts = c.getPoints();

        List<Vec2> bottom = pts.stream().filter(p -> Math.abs(p.y()) < 0.02).toList();
        assertTrue(bottom.size() >= 2);
        for (Vec2 p : bottom) assertEquals(0.0, p.y(), 0.02);

        List<Vec2> right = pts.stream().filter(p -> Math.abs(p.x() - 100) < 0.02).toList();
        assertTrue(right.size() >= 2);
        for (Vec2 p : right) assertEquals(100.0, p.x(), 0.02);

        for (Vec2 p : pts) {
            if (p.x() > 44 && p.x() < 99 && Math.abs(p.y() - 40) < 0.5) {
                assertEquals(40.0, p.y(), 0.05, "bent off horizontal arm: " + p);
            }
        }
    }

    @Test
    void holeDogbonesIntoPlateSolid() {
        GeometryModel m = new GeometryModel();
        Contour outer = new Contour();
        outer.setClosed(true);
        outer.setPoints(List.of(
                new Vec2(0, 0), new Vec2(200, 0), new Vec2(200, 200), new Vec2(0, 200)));
        Contour hole = new Contour();
        hole.setClosed(true);
        hole.setPoints(List.of(
                new Vec2(50, 50), new Vec2(150, 50), new Vec2(150, 150), new Vec2(50, 150)));
        m.getContours().add(outer);
        m.getContours().add(hole);

        assertTrue(svc.countCandidates(m) >= 4);
        assertTrue(svc.apply(m, tool(6), 1.0).corners() >= 4);

        List<Vec2> hpts = hole.getPoints();
        // Overcut into plate (outside the original hole square)
        assertTrue(hpts.stream()
                .anyMatch(p -> p.x() < 49.5 || p.y() < 49.5 || p.x() > 150.5 || p.y() > 150.5),
                "dog-bones must overcut into plate solid; pts=" + hpts);

        // Arc endpoints remain on the original hole edge lines
        assertTrue(hpts.stream().anyMatch(p -> Math.abs(p.y() - 50) < 0.05),
                "must retain points on original bottom edge y=50; pts=" + hpts);
        assertTrue(hpts.stream().anyMatch(p -> Math.abs(p.x() - 50) < 0.05),
                "must retain points on original left edge x=50; pts=" + hpts);
        assertTrue(hpts.stream().anyMatch(p -> Math.abs(p.y() - 150) < 0.05),
                "must retain points on original top edge y=150; pts=" + hpts);
        assertTrue(hpts.stream().anyMatch(p -> Math.abs(p.x() - 150) < 0.05),
                "must retain points on original right edge x=150; pts=" + hpts);
    }

    private static boolean containsNear(List<Vec2> pts, Vec2 target, double eps) {
        return pts.stream().anyMatch(p -> p.dist(target) <= eps);
    }

    private static GeometryModel lShape() {
        GeometryModel m = new GeometryModel();
        Contour c = new Contour();
        c.setClosed(true);
        c.setPoints(List.of(
                new Vec2(0, 0), new Vec2(100, 0), new Vec2(100, 40),
                new Vec2(40, 40), new Vec2(40, 100), new Vec2(0, 100)));
        m.getContours().add(c);
        return m;
    }

    private static Tool tool(double d) {
        Tool t = new Tool();
        t.setDiameterMm(d);
        t.setName(d + "mm");
        return t;
    }
}
