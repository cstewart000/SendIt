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
    void outerInternalCorner_LShape_intoMaterial() {
        GeometryModel m = lShape();
        Contour c = m.getContours().get(0);
        Vec2 corner = new Vec2(40, 40);

        assertEquals(1, svc.countCandidates(m), "L has exactly one internal corner");
        DogBoneService.Result r = svc.apply(m, tool(6), 1.0);
        assertEquals(1, r.corners());
        assertEquals(3.0, r.radiusMm(), 0.01);

        List<Vec2> pts = c.getPoints();
        assertTrue(pts.size() > 20, "size=" + pts.size());
        assertTrue(containsNear(pts, corner, 0.05), "vertex remains on circle rim");

        // Body into solid L
        assertTrue(pts.stream().anyMatch(p -> p.x() < 38 && p.y() < 38),
                "must cut into part material");
        assertFalse(pts.stream().anyMatch(p -> p.x() > 42 && p.y() > 42),
                "must not bulge into free space");

        // Outer vertices and straight edges preserved
        assertTrue(containsNear(pts, new Vec2(0, 0), 0.05));
        assertTrue(containsNear(pts, new Vec2(100, 0), 0.05));
        assertTrue(containsNear(pts, new Vec2(100, 40), 0.05));
        assertTrue(containsNear(pts, new Vec2(40, 100), 0.05));
        assertTrue(containsNear(pts, new Vec2(0, 100), 0.05));
        assertTrue(pts.stream().anyMatch(p -> Math.abs(p.y() - 40) < 0.05 && p.x() > 50));
        assertTrue(pts.stream().anyMatch(p -> Math.abs(p.x() - 40) < 0.05 && p.y() > 50));
    }

    @Test
    void outerInternalCorner_cwWinding_alsoWorks() {
        // Same L, opposite winding
        GeometryModel m = model(contour(
                p(0, 0), p(0, 100), p(40, 100), p(40, 40), p(100, 40), p(100, 0)));
        Contour c = m.getContours().get(0);
        assertEquals(1, svc.countCandidates(m));
        assertEquals(1, svc.apply(m, tool(6), 1.0).corners());
        assertTrue(c.getPoints().stream().anyMatch(pt -> pt.x() < 38 && pt.y() < 38));
        assertFalse(c.getPoints().stream().anyMatch(pt -> pt.x() > 42 && pt.y() > 42));
    }

    @Test
    void outerConvexSquare_noDogbones() {
        GeometryModel m = model(contour(p(0, 0), p(50, 0), p(50, 50), p(0, 50)));
        assertEquals(0, svc.countCandidates(m));
        assertEquals(0, svc.apply(m, tool(6), 1.0).corners());
        assertEquals(4, m.getContours().get(0).getPoints().size());
    }

    @Test
    void uShape_twoInternalCorners_notIntoPocket() {
        GeometryModel m = model(contour(
                p(0, 0), p(100, 0), p(100, 100),
                p(70, 100), p(70, 40), p(30, 40),
                p(30, 100), p(0, 100)));
        Contour c = m.getContours().get(0);
        assertEquals(2, svc.countCandidates(m), "U has 2 internal corners");
        assertEquals(2, svc.apply(m, tool(6), 1.0).corners());
        // Free pocket is 30<x<70, y>40 — dogbones must not sit there
        assertFalse(c.getPoints().stream().anyMatch(pt ->
                        pt.x() > 35 && pt.x() < 65 && pt.y() > 45),
                "must not dogbone into U free pocket");
        // Must overcut into solid (base or arms)
        assertTrue(c.getPoints().stream().anyMatch(pt -> pt.y() < 38),
                "must overcut into solid below the slot");
    }

    @Test
    void holeCcw_fourInternalCorners_intoPlate() {
        GeometryModel m = model(
                contour(p(0, 0), p(200, 0), p(200, 200), p(0, 200)),
                contour(p(50, 50), p(150, 50), p(150, 150), p(50, 150)));
        Contour outer = m.getContours().get(0);
        Contour hole = m.getContours().get(1);

        assertEquals(4, svc.countCandidates(m));
        assertEquals(4, svc.apply(m, tool(6), 1.0).corners());
        assertEquals(4, outer.getPoints().size(), "outer convex corners unchanged");

        List<Vec2> hpts = hole.getPoints();
        assertTrue(hpts.size() > 20);
        // Overcut into plate (outside hole)
        assertTrue(hpts.stream().anyMatch(p ->
                p.x() < 49.5 || p.y() < 49.5 || p.x() > 150.5 || p.y() > 150.5));
        // Not into the hole void
        assertFalse(hpts.stream().anyMatch(p ->
                        p.x() > 55 && p.x() < 145 && p.y() > 55 && p.y() < 145),
                "dogbone body must not sit in hole void");
        // Original corners remain on rims
        assertTrue(containsNear(hpts, new Vec2(50, 50), 0.05));
        assertTrue(containsNear(hpts, new Vec2(150, 50), 0.05));
        assertTrue(containsNear(hpts, new Vec2(150, 150), 0.05));
        assertTrue(containsNear(hpts, new Vec2(50, 150), 0.05));
    }

    @Test
    void holeCw_fourInternalCorners_intoPlate() {
        GeometryModel m = model(
                contour(p(0, 0), p(200, 0), p(200, 200), p(0, 200)),
                contour(p(50, 50), p(50, 150), p(150, 150), p(150, 50)));
        Contour hole = m.getContours().get(1);
        assertEquals(4, svc.countCandidates(m));
        assertEquals(4, svc.apply(m, tool(6), 1.0).corners());
        assertTrue(hole.getPoints().stream().anyMatch(p ->
                p.x() < 49.5 || p.y() < 49.5 || p.x() > 150.5 || p.y() > 150.5));
        assertFalse(hole.getPoints().stream().anyMatch(p ->
                p.x() > 55 && p.x() < 145 && p.y() > 55 && p.y() < 145));
    }

    @Test
    void frame_onlyHoleGetsDogbones() {
        GeometryModel m = model(
                contour(p(0, 0), p(100, 0), p(100, 100), p(0, 100)),
                contour(p(20, 20), p(80, 20), p(80, 80), p(20, 80)));
        assertEquals(4, svc.countCandidates(m));
        assertEquals(4, svc.apply(m, tool(6), 1.0).corners());
        assertEquals(4, m.getContours().get(0).getPoints().size());
        assertTrue(m.getContours().get(1).getPoints().size() > 20);
    }

    @Test
    void lOuterPlusSquareHole_bothGetInternalDogbones() {
        GeometryModel m = model(
                contour(p(0, 0), p(200, 0), p(200, 80), p(80, 80), p(80, 200), p(0, 200)),
                contour(p(20, 20), p(60, 20), p(60, 60), p(20, 60)));
        // L has 1 internal + hole has 4
        assertEquals(5, svc.countCandidates(m));
        assertEquals(5, svc.apply(m, tool(6), 1.0).corners());

        Contour outer = m.getContours().get(0);
        Contour hole = m.getContours().get(1);
        // L re-entrant at (80,80) into solid
        assertTrue(outer.getPoints().stream().anyMatch(p -> p.x() < 78 && p.y() < 78));
        // Hole into plate
        assertTrue(hole.getPoints().stream().anyMatch(p ->
                p.x() < 19.5 || p.y() < 19.5 || p.x() > 60.5 || p.y() > 60.5));
    }

    @Test
    void circlePassesThroughOriginalVertexCentreInMaterial() {
        List<Vec2> poly = List.of(
                new Vec2(0, 0), new Vec2(100, 0), new Vec2(100, 40),
                new Vec2(40, 40), new Vec2(40, 100), new Vec2(0, 100));
        Vec2 a = new Vec2(100, 40), b = new Vec2(40, 40), c = new Vec2(40, 100);
        double r = 3.0;
        List<Vec2> lobe = svc.dogboneLobe(a, b, c, r, poly, false);
        assertTrue(lobe.size() >= 20);

        assertEquals(0.0, lobe.get(0).dist(b), 1e-9);
        assertEquals(0.0, lobe.get(lobe.size() - 1).dist(b), 1e-9);

        double sx = 0, sy = 0;
        for (Vec2 p : lobe) { sx += p.x(); sy += p.y(); }
        Vec2 mean = new Vec2(sx / lobe.size(), sy / lobe.size());
        assertTrue(mean.dist(b) > r * 0.5, "centre offset from vertex");
        assertTrue(mean.x() < 40 && mean.y() < 40, "centre in material");
        assertEquals(r, mean.dist(b), 0.15);

        for (Vec2 p : lobe) {
            assertEquals(r, p.dist(mean), 0.2);
        }
        Vec2 deep = lobe.stream().max((p, q) -> Double.compare(p.dist(b), q.dist(b))).orElseThrow();
        assertTrue(deep.x() < 40 && deep.y() < 40);
        assertEquals(2 * r, deep.dist(b), 0.35);
    }

    @Test
    void holeLobeCentreInPlateNotInVoid() {
        List<Vec2> outer = List.of(p(0, 0), p(200, 0), p(200, 200), p(0, 200));
        List<Vec2> hole = List.of(p(50, 50), p(150, 50), p(150, 150), p(50, 150));
        // Corner (50,50): edges toward (50,150) and (150,50)
        Vec2 a = p(50, 150), b = p(50, 50), c = p(150, 50);
        List<Vec2> lobe = svc.dogboneLobe(a, b, c, 3.0, hole, true, List.of(outer));
        assertTrue(lobe.size() >= 20);

        double sx = 0, sy = 0;
        for (Vec2 pt : lobe) { sx += pt.x(); sy += pt.y(); }
        Vec2 mean = new Vec2(sx / lobe.size(), sy / lobe.size());
        // Centre in plate: outside hole near (50,50) → x<50 or y<50
        assertTrue(mean.x() < 50 || mean.y() < 50, "centre in plate; mean=" + mean);
        assertFalse(mean.x() > 50 && mean.y() > 50, "centre must not be in hole void; mean=" + mean);
        assertEquals(3.0, mean.dist(b), 0.2);
    }

    private static boolean containsNear(List<Vec2> pts, Vec2 target, double eps) {
        return pts.stream().anyMatch(p -> p.dist(target) <= eps);
    }

    private static GeometryModel lShape() {
        return model(contour(
                p(0, 0), p(100, 0), p(100, 40),
                p(40, 40), p(40, 100), p(0, 100)));
    }

    private static Vec2 p(double x, double y) { return new Vec2(x, y); }

    private static Contour contour(Vec2... pts) {
        Contour c = new Contour();
        c.setClosed(true);
        c.setPoints(List.of(pts));
        return c;
    }

    private static GeometryModel model(Contour... cs) {
        GeometryModel m = new GeometryModel();
        for (Contour c : cs) m.getContours().add(c);
        return m;
    }

    private static Tool tool(double d) {
        Tool t = new Tool();
        t.setDiameterMm(d);
        t.setName(d + "mm");
        return t;
    }
}
