package com.timbernest.geometry;

import com.timbernest.geometry.model.Contour;
import com.timbernest.geometry.model.GeometryModel;
import com.timbernest.geometry.model.Vec2;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Reproduces Voron-style DXF: outer profile as LINE+ARC chain + CIRCLE holes.
 * Without join/arc-wrap, only circles are closed → 3D cylinder.
 */
class ContourJoinArcTest {

    @TempDir Path tmp;

    @Test
    void joinsLineArcChainIntoClosedOuter() {
        GeometryModel model = new GeometryModel();
        // Rounded rectangle: bottom line, right arc (90°), top line, left arc (90°)
        // Bottom: (10,0) → (90,0)
        model.getContours().add(line("L1", 10, 0, 90, 0));
        // Arc bottom-right: center (90,10), r=10, from 270° to 0° (CCW wrap)
        model.getContours().add(DxfParser.arcDegrees(90, 10, 10, 270, 0));
        model.getContours().get(1).setId("A1");
        // Right: (100,10) → (100,90)
        model.getContours().add(line("L2", 100, 10, 100, 90));
        // Arc top-right: center (90,90), r=10, 0→90
        model.getContours().add(DxfParser.arcDegrees(90, 90, 10, 0, 90));
        model.getContours().get(3).setId("A2");
        // Top: (90,100) → (10,100)
        model.getContours().add(line("L3", 90, 100, 10, 100));
        // Arc top-left: center (10,90), r=10, 90→180
        model.getContours().add(DxfParser.arcDegrees(10, 90, 10, 90, 180));
        model.getContours().get(5).setId("A3");
        // Left: (0,90) → (0,10)
        model.getContours().add(line("L4", 0, 90, 0, 10));
        // Arc bottom-left: center (10,10), r=10, 180→270
        model.getContours().add(DxfParser.arcDegrees(10, 10, 10, 180, 270));
        model.getContours().get(7).setId("A4");
        // Hole circle
        Contour hole = ArcTessellator.circle(50, 50, 8);
        hole.setId("C1");
        model.getContours().add(hole);

        assertEquals(0, model.getContours().stream().filter(Contour::isClosed).filter(c -> !c.getId().startsWith("C")).count());

        ContourJoiner.joinAdaptive(model);

        long closed = model.getContours().stream().filter(Contour::isClosed).count();
        assertTrue(closed >= 2, "outer + hole should be closed, closed=" + closed);

        PartExtractor pe = new PartExtractor();
        List<PartExtractor.ExtractedPart> parts = pe.extract(model);
        assertEquals(1, parts.size(), "one nestable part (outer with hole)");
        assertTrue(parts.get(0).widthMm() > 90);
        assertTrue(parts.get(0).heightMm() > 90);
        assertTrue(parts.get(0).geometry().getContours().size() >= 2, "outer + hole in part geometry");
    }

    @Test
    void arcWrapEndLessThanStart() {
        Contour arc = DxfParser.arcDegrees(0, 0, 10, 350, 10);
        assertFalse(arc.isClosed());
        assertTrue(arc.getPoints().size() >= 8);
        Vec2 start = arc.getPoints().get(0);
        Vec2 end = arc.getPoints().get(arc.getPoints().size() - 1);
        // 350° ≈ (-0.98*r? cos350≈0.985, sin≈-0.174) * 10
        assertEquals(10 * Math.cos(Math.toRadians(350)), start.x(), 1e-6);
        assertEquals(10 * Math.cos(Math.toRadians(10)), end.x(), 1e-6);
        // Span should be 20° not -340°
        double chord = start.dist(end);
        assertTrue(chord < 5, "short 20° arc chord, got " + chord);
    }

    @Test
    void parsesLineArcDxfAndExtractsPart(@TempDir Path dir) throws Exception {
        Path dxf = dir.resolve("rounded-plate.dxf");
        Files.writeString(dxf, roundedPlateDxf());
        GeometryModel model = new DxfParser().parse(dxf);
        long closed = model.getContours().stream().filter(Contour::isClosed).count();
        assertTrue(closed >= 2, "expected closed outer+hole, closed=" + closed + " total=" + model.getContours().size());
        List<PartExtractor.ExtractedPart> parts = new PartExtractor().extract(model);
        assertEquals(1, parts.size());
        assertTrue(parts.get(0).geometry().getContours().size() >= 2);
    }

    @Test
    void lwPolylineBulgeMakesArcCorners() {
        // Unit semicircle bulge=1 from (0,0) to (2,0) should pass near (1,1)
        List<Vec2> pts = new java.util.ArrayList<>();
        pts.add(new Vec2(0, 0));
        DxfParser.appendBulge(pts, new Vec2(0, 0), new Vec2(2, 0), 1.0);
        assertTrue(pts.size() > 3);
        assertEquals(2.0, pts.get(pts.size() - 1).x(), 1e-6);
        double maxAbsY = pts.stream().mapToDouble(p -> Math.abs(p.y())).max().orElse(0);
        assertTrue(maxAbsY > 0.8, "semicircle should reach |y|~1, maxAbsY=" + maxAbsY);
    }

    private static Contour line(String id, double x0, double y0, double x1, double y1) {
        Contour c = new Contour();
        c.setId(id);
        c.setClosed(false);
        c.getPoints().add(new Vec2(x0, y0));
        c.getPoints().add(new Vec2(x1, y1));
        return c;
    }

    private static String roundedPlateDxf() {
        // Same rounded rect as join test, plus center hole — pure entity codes
        StringBuilder sb = new StringBuilder();
        sb.append("0\nSECTION\n2\nENTITIES\n");
        lineEnt(sb, 10, 0, 90, 0);
        arcEnt(sb, 90, 10, 10, 270, 0);
        lineEnt(sb, 100, 10, 100, 90);
        arcEnt(sb, 90, 90, 10, 0, 90);
        lineEnt(sb, 90, 100, 10, 100);
        arcEnt(sb, 10, 90, 10, 90, 180);
        lineEnt(sb, 0, 90, 0, 10);
        arcEnt(sb, 10, 10, 10, 180, 270);
        // circle hole
        sb.append("0\nCIRCLE\n8\n0\n10\n50\n20\n50\n40\n8\n");
        sb.append("0\nENDSEC\n0\nEOF\n");
        return sb.toString();
    }

    private static void lineEnt(StringBuilder sb, double x0, double y0, double x1, double y1) {
        sb.append("0\nLINE\n8\n0\n");
        sb.append("10\n").append(x0).append("\n20\n").append(y0).append("\n");
        sb.append("11\n").append(x1).append("\n21\n").append(y1).append("\n");
    }

    private static void arcEnt(StringBuilder sb, double cx, double cy, double r, double a0, double a1) {
        sb.append("0\nARC\n8\n0\n");
        sb.append("10\n").append(cx).append("\n20\n").append(cy).append("\n40\n").append(r).append("\n");
        sb.append("50\n").append(a0).append("\n51\n").append(a1).append("\n");
    }
}
