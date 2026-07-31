package com.timbernest.geometry;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DwgParserTest {
    private static final Path DWG = Path.of("../samples/demo-r2000.dwg").toAbsolutePath().normalize();

    static boolean samplePresent() {
        return Files.isRegularFile(DWG);
    }

    @Test
    @EnabledIf("samplePresent")
    void parsesSampleR2000() {
        var model = new DwgParser().parse(DWG);
        assertFalse(model.getContours().isEmpty());
        assertTrue(model.getContours().stream().anyMatch(c -> c.getPoints().size() >= 2));
    }
}
