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
    void centreInFreeSpace_vertexOnRim_arcOvercutsSolid() {
        List<Vec2> poly = List.of(
                new Vec2(0, 0), new Vec2(100, 0), new Vec2(100, 40),
                new Vec2(40, 40), new Vec2(40, 100), new Vec2(0, 100));
        Vec2 a = new Vec2(100, 40), b = new Vec2(40, 40), c = new Vec2(40, 100);
        double r = 3.0;
        List<Vec2> lobe = svc.dogboneLobe(a, b, c, r, poly, false);
        assertTrue(lobe.size() >= 8, "samples=" + lobe.size());

        // Endpoints on original edges
        Vec2 p1 = lobe.get(0), p2 = lobe.get(lobe.size() - 1);
        assertEquals(40.0, p1.y(), 1e-3);
        assertTrue(p1.x() > 40, "p1=" + p1);
        assertEquals(40.0, p2.x(), 1e-3);
        assertTrue(p2.y() > 40, "p2=" + p2);

        // Infer centre from chord circumcircle of p1, mid, p2 — or mean of samples
        // All samples equidistant from free-space centre
        // Free-space centre for 90°: B + free*r ≈ (42.12, 42.12)
        Vec2 expectedC = new Vec2(40 + r / Math.sqrt(2), 40 + r / Math.sqrt(2));
        assertTrue(expectedC.x() > 40 && expectedC.y() > 40, "centre must be in FREE space");

        for (Vec2 p : lobe) {
            assertEquals(r, p.dist(expectedC), 0.15, "off circle: " + p);
        }
        // Vertex on rim
        assertEquals(r, b.dist(expectedC), 1e-6);

        // Arc overcuts solid (into the L arms), not only free space
        assertTrue(lobe.stream().anyMatch(p -> p.x() < 40.05 && p.y() < 42.5),
                "must overcut into solid near vertical wall");
        assertTrue(lobe.stream().anyMatch(p -> p.y() < 40.05 && p.x() < 42.5),
                "must overcut into solid near horizontal wall");
        // Mid of free-space short arc would be ~ (44,44) — must not be the only path
        // Chosen arc should pass near original vertex
        assertTrue(lobe.stream().anyMatch(p -> p.dist(b) < 0.5),
                "arc should pass near original vertex on the rim");
    }

    @Test
    void apply_LShape_centreFree_overcutSolid_edgesPreserved() {
        GeometryModel m = lShape();
        Contour c = m.getContours().get(0);
        assertEquals(1, svc.countCandidates(m));
        assertEquals(1, svc.apply(m, tool(6), 1.0).corners());

        List<Vec2> pts = c.getPoints();
        assertTrue(pts.size() > 10);

        // Overcut into solid arms
        assertTrue(pts.stream().anyMatch(p -> p.x() < 40 && p.y() < 43),
                "overcut into solid");
        // Must not place centre-style full lobe deep in free space only without solid cut
        // Deep free-only points beyond tool reach shouldn't dominate
        // Outer vertices preserved
        assertTrue(containsNear(pts, new Vec2(0, 0), 0.05));
        assertTrue(containsNear(pts, new Vec2(100, 0), 0.05));
        assertTrue(containsNear(pts, new Vec2(100, 40), 0.05));
        assertTrue(containsNear(pts, new Vec2(40, 100), 0.05));
        assertTrue(containsNear(pts, new Vec2(0, 100), 0.05));

        // Straight edges beyond setback stay on lines
        assertTrue(pts.stream().anyMatch(p -> Math.abs(p.y() - 40) < 0.05 && p.x() > 50));
        assertTrue(pts.stream().anyMatch(p -> Math.abs(p.x() - 40) < 0.05 && p.y() > 50));
    }

    @Test
    void outerConvexSquare_noDogbones() {
        GeometryModel m = model(contour(p(0, 0), p(50, 0), p(50, 50), p(0, 50)));
        assertEquals(0, svc.countCandidates(m));
        assertEquals(0, svc.apply(m, tool(6), 1.0).corners());
    }

    @Test
    void holeCcw_centreInVoid_overcutIntoPlate() {
        GeometryModel m = model(
                contour(p(0, 0), p(200, 0), p(200, 200), p(0, 200)),
                contour(p(50, 50), p(150, 50), p(150, 150), p(50, 150)));
        Contour outer = m.getContours().get(0);
        Contour hole = m.getContours().get(1);

        assertEquals(4, svc.countCandidates(m));
        assertEquals(4, svc.apply(m, tool(6), 1.0).corners());
        assertEquals(4, outer.getPoints().size());

        List<Vec2> hpts = hole.getPoints();
        // Overcut into plate outside the hole square
        assertTrue(hpts.stream().anyMatch(p ->
                p.x() < 49.5 || p.y() < 49.5 || p.x() > 150.5 || p.y() > 150.5),
                "must overcut into plate");
        // Edges beyond dogbones preserved (setback ≈ r√2 ≈ 4.2 for r=3)
        assertTrue(hpts.stream().anyMatch(p -> Math.abs(p.y() - 50) < 0.15 && p.x() > 53 && p.x() < 147),
                "bottom hole edge must remain on y=50");
    }

    @Test
    void holeLobe_centreInsideHole_notInPlate() {
        List<Vec2> outer = List.of(p(0, 0), p(200, 0), p(200, 200), p(0, 200));
        List<Vec2> hole = List.of(p(50, 50), p(150, 50), p(150, 150), p(50, 150));
        Vec2 a = p(50, 150), b = p(50, 50), c = p(150, 50);
        double r = 3.0;
        List<Vec2> lobe = svc.dogboneLobe(a, b, c, r, hole, true, List.of(outer));
        assertTrue(lobe.size() >= 8);

        // Expected free-space centre inside hole
        Vec2 expectedC = new Vec2(50 + r / Math.sqrt(2), 50 + r / Math.sqrt(2));
        assertTrue(expectedC.x() > 50 && expectedC.y() > 50, "centre in hole void");
        assertEquals(r, b.dist(expectedC), 1e-6);

        for (Vec2 pt : lobe) {
            assertEquals(r, pt.dist(expectedC), 0.2);
        }
        // Arc reaches into plate
        assertTrue(lobe.stream().anyMatch(pt -> pt.x() < 50 || pt.y() < 50),
                "arc must overcut into plate");
    }

    @Test
    void holeCw_fourCorners() {
        GeometryModel m = model(
                contour(p(0, 0), p(200, 0), p(200, 200), p(0, 200)),
                contour(p(50, 50), p(50, 150), p(150, 150), p(150, 50)));
        assertEquals(4, svc.countCandidates(m));
        assertEquals(4, svc.apply(m, tool(6), 1.0).corners());
        assertTrue(m.getContours().get(1).getPoints().stream().anyMatch(p ->
                p.x() < 49.5 || p.y() < 49.5 || p.x() > 150.5 || p.y() > 150.5));
    }

    @Test
    void uShape_twoInternal_notIntoPocketFreeSpaceOnly() {
        GeometryModel m = model(contour(
                p(0, 0), p(100, 0), p(100, 100),
                p(70, 100), p(70, 40), p(30, 40),
                p(30, 100), p(0, 100)));
        assertEquals(2, svc.countCandidates(m));
        assertEquals(2, svc.apply(m, tool(6), 1.0).corners());
        Contour c = m.getContours().get(0);
        // Centres sit in the pocket free space (30–70, y>40) near the internal corners
        // Overcut must reach solid (y < 40 or into the arms)
        assertTrue(c.getPoints().stream().anyMatch(pt -> pt.y() < 40.1),
                "must overcut into solid below slot floor");
    }

    @Test
    void lOuterPlusHole_bothInternal() {
        GeometryModel m = model(
                contour(p(0, 0), p(200, 0), p(200, 80), p(80, 80), p(80, 200), p(0, 200)),
                contour(p(20, 20), p(60, 20), p(60, 60), p(20, 60)));
        assertEquals(5, svc.countCandidates(m));
        assertEquals(5, svc.apply(m, tool(6), 1.0).corners());
    }

    @Test
    void outerCw_L_alsoWorks() {
        GeometryModel m = model(contour(
                p(0, 0), p(0, 100), p(40, 100), p(40, 40), p(100, 40), p(100, 0)));
        assertEquals(1, svc.countCandidates(m));
        assertEquals(1, svc.apply(m, tool(6), 1.0).corners());
        assertTrue(m.getContours().get(0).getPoints().stream()
                .anyMatch(pt -> pt.x() < 40.1 && pt.y() < 43));
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
