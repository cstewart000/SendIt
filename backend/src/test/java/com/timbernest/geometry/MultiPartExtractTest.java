package com.timbernest.geometry;

import com.timbernest.geometry.model.GeometryModel;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MultiPartExtractTest {

    @Test
    void multiPartKitExtractsThreeParts() {
        Path dxf = Path.of("../samples/multi-part-kit.dxf").toAbsolutePath().normalize();
        GeometryModel model = new DxfParser().parse(dxf);
        List<PartExtractor.ExtractedPart> parts = new PartExtractor().extract(model);
        assertEquals(3, parts.size(), "multi-part-kit.dxf has 3 separate outers");
        assertTrue(parts.stream().allMatch(p -> p.widthMm() > 50 && p.heightMm() > 30));
    }
}
