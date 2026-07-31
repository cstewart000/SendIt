package com.timbernest.cam;

import com.timbernest.geometry.model.Vec2;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ContourOffsetTest {

    @Test
    void expandsCcwSquareOutward() {
        List<Vec2> sq = square(0, 0, 100);
        List<Vec2> out = ContourOffset.outerToolpath(sq, 5);
        assertEquals(4, out.size());
        double[] b = bounds(out);
        // Expanded ~5mm each side → width/height ~110
        assertEquals(110, b[2] - b[0], 1.5);
        assertEquals(110, b[3] - b[1], 1.5);
        assertTrue(ContourOffset.absArea(out) > ContourOffset.absArea(sq));
    }

    @Test
    void contractsHoleInward() {
        List<Vec2> sq = square(0, 0, 100);
        List<Vec2> hole = ContourOffset.holeToolpath(sq, 5);
        double[] b = bounds(hole);
        assertEquals(90, b[2] - b[0], 1.5);
        assertEquals(90, b[3] - b[1], 1.5);
    }

    @Test
    void detectsCcw() {
        assertTrue(ContourOffset.isCcw(square(0, 0, 10)));
        List<Vec2> cw = List.of(
                new Vec2(0, 0), new Vec2(0, 10), new Vec2(10, 10), new Vec2(10, 0));
        assertFalse(ContourOffset.isCcw(cw));
    }

    private static List<Vec2> square(double x, double y, double s) {
        return List.of(
                new Vec2(x, y),
                new Vec2(x + s, y),
                new Vec2(x + s, y + s),
                new Vec2(x, y + s));
    }

    private static double[] bounds(List<Vec2> pts) {
        double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;
        for (Vec2 p : pts) {
            minX = Math.min(minX, p.x()); minY = Math.min(minY, p.y());
            maxX = Math.max(maxX, p.x()); maxY = Math.max(maxY, p.y());
        }
        return new double[]{minX, minY, maxX, maxY};
    }
}
