package com.timbernest.cam;

import com.timbernest.admin.Machine;
import com.timbernest.admin.Material;
import com.timbernest.admin.Tool;
import com.timbernest.geometry.model.Contour;
import com.timbernest.geometry.model.GeometryModel;
import com.timbernest.geometry.model.Vec2;
import com.timbernest.nesting.NestPlacement;
import com.timbernest.nesting.NestResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GCodeGeneratorTest {

    private final GCodeGenerator gen = new GCodeGenerator();

    @Test
    void holesBeforeOuterAndUsesSafeZAndOffset() {
        GeometryModel model = plateWithHole();
        NestResult nest = new NestResult();
        nest.setSheetCount(1);
        NestPlacement pl = new NestPlacement();
        pl.setLabel("Frame");
        pl.setX(20);
        pl.setY(30);
        pl.setNativeWidth(100);
        pl.setNativeHeight(100);
        pl.setWidth(100);
        pl.setHeight(100);
        pl.setRotationDeg(0);
        pl.setSheetIndex(0);
        nest.getPlacements().add(pl);

        Machine machine = new Machine();
        machine.setDefaultFeedMmMin(3000);
        machine.setDefaultSpeedRpm(18000);
        machine.setWorkZmm(80);
        Tool tool = new Tool();
        tool.setName("6mm");
        tool.setDiameterMm(6);
        tool.setMaxDepthMm(6);
        Material mat = new Material();
        mat.setThicknessMm(18);

        String nc = gen.generate(nest, List.of(model), nest.getPlacements(), machine, tool, mat);

        assertTrue(nc.contains("G21"));
        assertTrue(nc.contains("G40")); // offline offset, not live comp
        assertTrue(nc.contains("Z15.000") || nc.contains("Z15"));
        assertTrue(nc.contains("(hole)"));
        assertTrue(nc.contains("(outer)"));
        int holeAt = nc.indexOf("(hole)");
        int outerAt = nc.indexOf("(outer)");
        assertTrue(holeAt >= 0 && outerAt > holeAt, "holes must be cut before outer profile");
        // multi-pass for 18mm / 6mm step → 3 passes
        long zCuts = nc.lines().filter(l -> l.startsWith("G1 Z-")).count();
        assertTrue(zCuts >= 6, "expected multi-pass plunges for hole+outer, got " + zCuts);
        assertTrue(nc.contains("M30"));
    }

    @Test
    void pathMetricsIncludeMultiPassLength() {
        GeometryModel model = plateWithHole();
        NestResult nest = new NestResult();
        nest.setSheetCount(1);
        NestPlacement pl = new NestPlacement();
        pl.setNativeWidth(100);
        pl.setNativeHeight(100);
        pl.setWidth(100);
        pl.setHeight(100);
        nest.getPlacements().add(pl);

        Machine machine = new Machine();
        machine.setDefaultFeedMmMin(3000);
        Tool tool = new Tool();
        tool.setDiameterMm(6);
        tool.setMaxDepthMm(6);
        Material mat = new Material();
        mat.setThicknessMm(18);

        PathMetrics.Result m = PathMetrics.compute(nest, List.of(model), machine, tool, mat);
        assertEquals(3, m.passes());
        assertTrue(m.cutLengthMm() > 400, "multi-pass cut length should exceed single loop");
        assertTrue(m.cycleMinutes() > 0);
        assertEquals(2, m.contourCount());
    }

    private static GeometryModel plateWithHole() {
        GeometryModel m = new GeometryModel();
        Contour outer = new Contour();
        outer.setId("O");
        outer.setClosed(true);
        outer.setPoints(List.of(
                new Vec2(0, 0), new Vec2(100, 0), new Vec2(100, 100), new Vec2(0, 100)));
        Contour hole = new Contour();
        hole.setId("H");
        hole.setClosed(true);
        hole.setPoints(List.of(
                new Vec2(30, 30), new Vec2(70, 30), new Vec2(70, 70), new Vec2(30, 70)));
        m.getContours().add(outer);
        m.getContours().add(hole);
        return m;
    }
}
