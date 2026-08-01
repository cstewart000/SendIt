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
    void addsCircularArcOnInternalCornerOfLShape() {
        GeometryModel m = lShape();
        Contour c = m.getContours().get(0);
        int before = c.getPoints().size();

        assertTrue(svc.countCandidates(m) >= 1, "expected internal corner at re-entrant");

        Tool t = tool(6);
        DogBoneService.Result r = svc.apply(m, t, 1.0);
        assertTrue(r.corners() >= 1, "should apply at least one dog-bone");
        assertEquals(3.0, r.radiusMm(), 0.01);

        int after = c.getPoints().size();
        // Circular arc inserts many points (not just +1 spike)
        assertTrue(after >= before + 8, "expected arc tessellation, points " + before + "→" + after);

        // Arc should extend into the missing quadrant near (40,40) — points with x>40 and y>40
        boolean intoWaste = c.getPoints().stream().anyMatch(p -> p.x() > 41 && p.y() > 41);
        assertTrue(intoWaste, "dog-bone arc should bulge into L waste (x>40,y>40)");
    }

    @Test
    void dogbonesOnRectangularHoleCorners() {
        GeometryModel m = new GeometryModel();
        // Outer plate
        Contour outer = new Contour();
        outer.setClosed(true);
        outer.setPoints(List.of(
                new Vec2(0, 0), new Vec2(200, 0), new Vec2(200, 200), new Vec2(0, 200)));
        // Rectangular hole (CCW)
        Contour hole = new Contour();
        hole.setClosed(true);
        hole.setPoints(List.of(
                new Vec2(50, 50), new Vec2(150, 50), new Vec2(150, 150), new Vec2(50, 150)));
        m.getContours().add(outer);
        m.getContours().add(hole);

        int cand = svc.countCandidates(m);
        assertTrue(cand >= 4, "hole corners should be candidates, got " + cand);

        DogBoneService.Result r = svc.apply(m, tool(6), 1.0);
        assertTrue(r.corners() >= 4, "expected dog-bones on hole corners, got " + r.corners());
        // Hole contour should have many more points from arcs
        assertTrue(hole.getPoints().size() > 4 + 4 * 8);
    }

    @Test
    void circularDogboneProducesCurvedArc() {
        List<Vec2> poly = List.of(
                new Vec2(0, 0), new Vec2(100, 0), new Vec2(100, 40),
                new Vec2(40, 40), new Vec2(40, 100), new Vec2(0, 100));
        Vec2 a = new Vec2(100, 40), b = new Vec2(40, 40), c = new Vec2(40, 100);
        List<Vec2> arc = svc.circularDogbone(a, b, c, 3.0, poly, false);
        assertTrue(arc.size() >= 10, "arc samples=" + arc.size());
        // Chord from first to last should be shorter than polyline path (curve bulges)
        double chord = arc.get(0).dist(arc.get(arc.size() - 1));
        double path = 0;
        for (int i = 1; i < arc.size(); i++) path += arc.get(i - 1).dist(arc.get(i));
        assertTrue(path > chord * 1.05, "path should exceed chord for a real curve");
    }

    private static GeometryModel lShape() {
        GeometryModel m = new GeometryModel();
        Contour c = new Contour();
        c.setClosed(true);
        c.setPoints(List.of(
                new Vec2(0, 0),
                new Vec2(100, 0),
                new Vec2(100, 40),
                new Vec2(40, 40),
                new Vec2(40, 100),
                new Vec2(0, 100)
        ));
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
