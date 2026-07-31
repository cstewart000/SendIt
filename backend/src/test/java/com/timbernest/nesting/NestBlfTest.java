package com.timbernest.nesting;

import com.timbernest.geometry.model.Vec2;
import com.timbernest.job.JobPart;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NestBlfTest {

    @Test
    void packsRectanglesOnOneSheet() {
        JobPart a = part("A", 100, 50);
        JobPart b = part("B", 80, 40);
        List<NestBlf.Piece> pieces = List.of(
                piece(a, 100, 50),
                piece(b, 80, 40)
        );
        NestBlf.PackResult r = NestBlf.pack(pieces, 500, 400, 10, 5);
        assertTrue(r.complete(), () -> "unplaced=" + r.unplaced());
        assertEquals(2, r.placements().size());
        assertEquals(1, r.sheetCount());
    }

    @Test
    void reportsUnplacedWhenPartLargerThanSheet() {
        JobPart huge = part("HUGE", 2000, 2000);
        List<NestBlf.Piece> pieces = List.of(piece(huge, 2000, 2000));
        NestBlf.PackResult r = NestBlf.pack(pieces, 500, 400, 10, 5);
        assertFalse(r.complete());
        assertTrue(r.unplaced().contains("HUGE"));
        assertTrue(r.placements().isEmpty());
    }

    @Test
    void multiQuantityUsesMultipleSheetsWhenNeeded() {
        JobPart p = part("Tile", 400, 300);
        List<NestBlf.Piece> pieces = new ArrayList<>();
        for (int i = 0; i < 4; i++) pieces.add(piece(p, 400, 300));
        NestBlf.PackResult r = NestBlf.pack(pieces, 500, 400, 10, 5);
        assertTrue(r.complete(), () -> "unplaced=" + r.unplaced());
        assertEquals(4, r.placements().size());
        assertTrue(r.sheetCount() >= 2);
    }

    private static JobPart part(String label, double w, double h) {
        JobPart p = new JobPart();
        p.setLabel(label);
        p.setWidthMm(w);
        p.setHeightMm(h);
        p.setQuantity(1);
        return p;
    }

    private static NestBlf.Piece piece(JobPart part, double w, double h) {
        List<Vec2> local = List.of(
                new Vec2(0, 0), new Vec2(w, 0), new Vec2(w, h), new Vec2(0, h));
        return new NestBlf.Piece(part, local, new double[]{0, 90, 180, 270});
    }
}
