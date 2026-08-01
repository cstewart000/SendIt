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
    void dogboneCentreOffsetVertexOnRimPreservesEdges() {
        GeometryModel m = lShape();
        Contour c = m.getContours().get(0);
        Vec2 corner = new Vec2(40, 40);

        assertTrue(svc.countCandidates(m) >= 1);
        DogBoneService.Result r = svc.apply(m, tool(6), 1.0);
        assertTrue(r.corners() >= 1);
        assertEquals(3.0, r.radiusMm(), 0.01);

        List<Vec2> pts = c.getPoints();
        assertTrue(pts.size() > 20, "size=" + pts.size());

        // Original vertex still present (rim of the dog-bone circle)
        assertTrue(containsNear(pts, corner, 0.05), "original vertex must remain on the circle rim");

        // Into solid L (x<40 and y<40)
        assertTrue(pts.stream().anyMatch(p -> p.x() < 38 && p.y() < 38),
                "dog-bone body must sit in part material");
        // Not into free space of L
        assertFalse(pts.stream().anyMatch(p -> p.x() > 42 && p.y() > 42),
                "must not bulge into free space");

        // Original non-corner vertices still present
        assertTrue(containsNear(pts, new Vec2(0, 0), 0.05));
        assertTrue(containsNear(pts, new Vec2(100, 0), 0.05));
        assertTrue(containsNear(pts, new Vec2(100, 40), 0.05));
        assertTrue(containsNear(pts, new Vec2(40, 100), 0.05));
        assertTrue(containsNear(pts, new Vec2(0, 100), 0.05));

        // Straight edges beyond the corner still on original lines
        assertTrue(pts.stream().anyMatch(p -> Math.abs(p.y() - 40) < 0.05 && p.x() > 50),
                "horizontal edge must stay on y=40");
        assertTrue(pts.stream().anyMatch(p -> Math.abs(p.x() - 40) < 0.05 && p.y() > 50),
                "vertical edge must stay on x=40");
    }

    @Test
    void circlePassesThroughOriginalVertexCentreInMaterial() {
        List<Vec2> poly = List.of(
                new Vec2(0, 0), new Vec2(100, 0), new Vec2(100, 40),
                new Vec2(40, 40), new Vec2(40, 100), new Vec2(0, 100));
        Vec2 a = new Vec2(100, 40), b = new Vec2(40, 40), c = new Vec2(40, 100);
        double r = 3.0;
        List<Vec2> lobe = svc.dogboneLobe(a, b, c, r, poly, false);
        assertTrue(lobe.size() >= 20, "samples=" + lobe.size());

        // Starts and ends at original vertex
        assertEquals(0.0, lobe.get(0).dist(b), 1e-9);
        assertEquals(0.0, lobe.get(lobe.size() - 1).dist(b), 1e-9);

        // Infer centre as average of samples (≈ geometric centre for full circle)
        double sx = 0, sy = 0;
        for (Vec2 p : lobe) { sx += p.x(); sy += p.y(); }
        Vec2 mean = new Vec2(sx / lobe.size(), sy / lobe.size());
        // Centre must NOT be at the vertex
        assertTrue(mean.dist(b) > r * 0.5, "centre must be offset from vertex; mean=" + mean);
        // Centre must be in material
        assertTrue(mean.x() < 40 && mean.y() < 40, "centre in material; mean=" + mean);
        // Distance centre → vertex ≈ r (vertex on rim)
        assertEquals(r, mean.dist(b), 0.15);

        // Every sample on the circle of radius r about the centre
        for (Vec2 p : lobe) {
            assertEquals(r, p.dist(mean), 0.2, "off circle: " + p);
        }

        // Deep sample opposite the vertex is further into material
        Vec2 deep = lobe.stream()
                .max((p, q) -> Double.compare(p.dist(b), q.dist(b)))
                .orElseThrow();
        assertTrue(deep.x() < 40 && deep.y() < 40, "deep=" + deep);
        assertEquals(2 * r, deep.dist(b), 0.35);
    }

    @Test
    void originalStraightSegmentsStayCollinear() {
        GeometryModel m = lShape();
        Contour c = m.getContours().get(0);
        svc.apply(m, tool(6), 1.0);
        List<Vec2> pts = c.getPoints();

        for (Vec2 p : pts) {
            if (Math.abs(p.y()) < 0.02) assertEquals(0.0, p.y(), 0.02);
            if (Math.abs(p.x() - 100) < 0.02) assertEquals(100.0, p.x(), 0.02);
            // Horizontal arm y=40 for x well beyond corner / lobe
            if (p.x() > 50 && p.x() < 99 && Math.abs(p.y() - 40) < 0.5) {
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
                "dog-bones must overcut into plate; pts count=" + hpts.size());

        // Original hole corners remain (on circle rims)
        assertTrue(containsNear(hpts, new Vec2(50, 50), 0.05));
        assertTrue(containsNear(hpts, new Vec2(150, 50), 0.05));
        assertTrue(containsNear(hpts, new Vec2(150, 150), 0.05));
        assertTrue(containsNear(hpts, new Vec2(50, 150), 0.05));
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
