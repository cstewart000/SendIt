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
    void addsDogbonesOnInternalCornersOfLShape() {
        // L-shape CCW with one sharp internal corner
        GeometryModel m = new GeometryModel();
        Contour c = new Contour();
        c.setClosed(true);
        c.setPoints(List.of(
                new Vec2(0, 0),
                new Vec2(100, 0),
                new Vec2(100, 40),
                new Vec2(40, 40), // internal corner at (40,40)
                new Vec2(40, 100),
                new Vec2(0, 100)
        ));
        m.getContours().add(c);

        int cand = svc.countCandidates(m);
        assertTrue(cand >= 1, "expected at least one internal corner");

        Tool t = new Tool();
        t.setDiameterMm(6);
        t.setName("6mm");
        DogBoneService.Result r = svc.apply(m, t, 1.0);
        assertTrue(r.corners() >= 1);
        assertEquals(3.0, r.radiusMm(), 0.01);
        // Geometry gained extra vertices
        assertTrue(c.getPoints().size() > 6);
    }
}
