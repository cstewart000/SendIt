package com.timbernest.geometry;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression for R2000+ LINE FIELD_DD fix (classpath override of LineObjectReader).
 * Uses sample DWG when present under samples/.
 */
class DwgLineFixTest {

    private static final Path PANINI = Path.of("../samples/2017-11-18 Sandwhich Panini Board All layouts.dwg")
            .toAbsolutePath().normalize();
    private static final Path DEMO = Path.of("../samples/demo-r2000.dwg")
            .toAbsolutePath().normalize();

    static boolean paniniPresent() {
        return Files.isRegularFile(PANINI);
    }

    @Test
    @EnabledIf("paniniPresent")
    void paniniHasNonAxisAlignedLines() {
        var model = new DwgParser().parse(PANINI);
        long lines = model.getContours().stream()
                .filter(c -> c.getId() != null && c.getId().startsWith("L")).count();
        long withY = model.getContours().stream()
                .filter(c -> c.getId() != null && c.getId().startsWith("L"))
                .filter(c -> c.getPoints().stream().anyMatch(p -> Math.abs(p.y()) > 1.0))
                .count();
        assertTrue(lines >= 10, "expected straight lines from DWG, got " + lines);
        assertTrue(withY >= 5, "expected lines with non-zero Y, got " + withY);
    }

    @Test
    void demoR2000ParsesSomeGeometry() {
        if (!Files.isRegularFile(DEMO)) return;
        var model = new DwgParser().parse(DEMO);
        assertTrue(model.getContours().size() >= 1);
        assertTrue(model.getContours().stream().anyMatch(c -> c.getPoints().size() >= 2));
    }
}
