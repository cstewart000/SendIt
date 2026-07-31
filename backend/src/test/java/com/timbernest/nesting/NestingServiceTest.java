package com.timbernest.nesting;

import com.timbernest.common.ApiException;
import com.timbernest.geometry.model.Contour;
import com.timbernest.geometry.model.GeometryModel;
import com.timbernest.geometry.model.Vec2;
import com.timbernest.job.JobPart;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class NestingServiceTest {

    private final NestingService nesting = new NestingService();

    @Test
    void failsClosedWhenPartDoesNotFit() {
        JobPart p = new JobPart();
        p.setLabel("Oversized");
        p.setWidthMm(3000);
        p.setHeightMm(2000);
        p.setQuantity(1);
        // id is null — geos keyed by null is fine for single part
        Map<Long, GeometryModel> geos = new HashMap<>();
        geos.put(null, rect(3000, 2000));

        ApiException ex = assertThrows(ApiException.class,
                () -> nesting.nest(List.of(p), geos, 2440, 1220, 10, 5));
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, ex.getStatus());
        assertTrue(ex.getMessage().contains("Oversized"));
    }

    @Test
    void nestsSimpleRect() {
        JobPart p = new JobPart();
        p.setLabel("Plate");
        p.setWidthMm(200);
        p.setHeightMm(100);
        p.setQuantity(2);
        Map<Long, GeometryModel> geos = new HashMap<>();
        geos.put(null, rect(200, 100));

        NestResult result = nesting.nest(List.of(p), geos, 2440, 1220, 10, 5);
        assertEquals(2, result.getPlacements().size());
        assertEquals(1, result.getSheetCount());
    }

    private static GeometryModel rect(double w, double h) {
        GeometryModel m = new GeometryModel();
        Contour c = new Contour();
        c.setClosed(true);
        c.setId("outer");
        c.setPoints(List.of(
                new Vec2(0, 0), new Vec2(w, 0), new Vec2(w, h), new Vec2(0, h)));
        m.getContours().add(c);
        return m;
    }
}
