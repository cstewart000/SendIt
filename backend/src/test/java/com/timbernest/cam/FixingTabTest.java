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

class FixingTabTest {

    @Test
    void plansFixingsOutsideParts() {
        NestPlacement pl = placement(50, 50, 100, 80);
        List<Vec2> outer = List.of(
                new Vec2(50, 50), new Vec2(150, 50), new Vec2(150, 130), new Vec2(50, 130));
        Machine m = machine();
        Tool t = tool(6);
        List<FixingPlanner.Candidate> c = FixingPlanner.plan(
                List.of(pl), List.of(outer), m, t, 600, 400, 10);
        assertFalse(c.isEmpty(), "expected screw holes around part");
        for (FixingPlanner.Candidate f : c) {
            assertEquals(4.0, f.diameterMm(), 0.01);
            // Outside part rectangle
            assertTrue(f.x() < 50 || f.x() > 150 || f.y() < 50 || f.y() > 130,
                    "fixing should be outside part: " + f.x() + "," + f.y());
        }
    }

    @Test
    void disabledFixingsExcluded() {
        List<FixingPlanner.Candidate> all = List.of(
                new FixingPlanner.Candidate("fix-0-0", 0, 1, 1, 4, 0, "A"),
                new FixingPlanner.Candidate("fix-0-1", 0, 2, 2, 4, 0, "A"));
        var active = FixingPlanner.applyDisabled(all, java.util.Set.of("fix-0-0"));
        assertEquals(1, active.size());
        assertEquals("fix-0-1", active.get(0).id());
    }

    @Test
    void tabsAlongOuter() {
        List<Vec2> outer = List.of(
                new Vec2(0, 0), new Vec2(200, 0), new Vec2(200, 100), new Vec2(0, 100));
        Machine m = machine();
        m.setTabCount(4);
        m.setTabWidthMm(5);
        List<TabPlanner.TabSpec> tabs = TabPlanner.plan(outer, 0, 0, m);
        assertEquals(4, tabs.size());
        assertTrue(tabs.get(0).widthMm() > 0);
    }

    @Test
    void gcodeIncludesDrillAndTabs() {
        GeometryModel model = rect(0, 0, 120, 80);
        NestPlacement pl = placement(20, 20, 120, 80);
        pl.setNativeWidth(120);
        pl.setNativeHeight(80);
        NestResult nest = new NestResult();
        nest.setSheetWidth(600);
        nest.setSheetHeight(400);
        nest.setMargin(10);
        nest.setSheetCount(1);
        nest.getPlacements().add(pl);

        Machine m = machine();
        Tool t = tool(6);
        Material mat = new Material();
        mat.setThicknessMm(18);

        ToolpathResult tp = new GCodeGenerator().build(nest, List.of(model), m, t, mat, new CamOptions());
        assertTrue(tp.getGcode().contains("Screw fixings") || tp.getGcode().contains("Fixing"),
                "gcode should mention fixings");
        assertFalse(tp.getFixings().isEmpty());
        assertFalse(tp.getTabs().isEmpty());
        assertTrue(tp.getPaths().stream().anyMatch(p -> "drill".equals(p.kind())));
        assertTrue(tp.getGcode().contains("outer with tabs") || tp.getGcode().contains("tabs"));
    }

    private static NestPlacement placement(double x, double y, double w, double h) {
        NestPlacement pl = new NestPlacement();
        pl.setX(x); pl.setY(y); pl.setWidth(w); pl.setHeight(h);
        pl.setNativeWidth(w); pl.setNativeHeight(h);
        pl.setSheetIndex(0); pl.setLabel("Part");
        pl.setRotationDeg(0);
        return pl;
    }

    private static Machine machine() {
        Machine m = new Machine();
        m.setKerfMm(8);
        m.setFixingMinToolDistanceMm(10);
        m.setFixingHoleDiameterMm(4);
        m.setFixingsEnabled(true);
        m.setTabsEnabled(true);
        m.setTabWidthMm(5);
        m.setTabHeightMm(1.5);
        m.setTabCount(4);
        m.setDefaultFeedMmMin(3000);
        m.setDefaultSpeedRpm(18000);
        m.setWorkZmm(80);
        return m;
    }

    private static Tool tool(double d) {
        Tool t = new Tool();
        t.setName(d + "mm");
        t.setDiameterMm(d);
        t.setMaxDepthMm(6);
        return t;
    }

    private static GeometryModel rect(double x, double y, double w, double h) {
        GeometryModel m = new GeometryModel();
        Contour c = new Contour();
        c.setClosed(true);
        c.setId("O");
        c.setPoints(List.of(
                new Vec2(x, y), new Vec2(x + w, y),
                new Vec2(x + w, y + h), new Vec2(x, y + h)));
        m.getContours().add(c);
        return m;
    }
}
