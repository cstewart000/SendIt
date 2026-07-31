package com.timbernest.cam;

import com.timbernest.admin.Machine;
import com.timbernest.admin.Material;
import com.timbernest.admin.Tool;
import com.timbernest.geometry.model.GeometryModel;
import com.timbernest.geometry.model.Vec2;
import com.timbernest.nesting.NestMath;
import com.timbernest.nesting.NestPlacement;
import com.timbernest.nesting.NestResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * LinuxCNC profile + drill fixings + hold-down tabs.
 * Offline tool-radius offset, holes-before-outer, multi-pass, machine safe-Z.
 */
@Component
public class GCodeGenerator {
    private static final Logger log = LoggerFactory.getLogger(GCodeGenerator.class);

    public String generate(NestResult nest, List<GeometryModel> models, List<NestPlacement> ignored,
                           Machine machine, Tool tool, Material material) {
        return build(nest, models, machine, tool, material, new CamOptions()).getGcode();
    }

    public ToolpathResult build(NestResult nest, List<GeometryModel> models,
                                Machine machine, Tool tool, Material material) {
        return build(nest, models, machine, tool, material, new CamOptions());
    }

    public ToolpathResult build(NestResult nest, List<GeometryModel> models,
                                Machine machine, Tool tool, Material material, CamOptions options) {
        if (options == null) options = new CamOptions();
        double radius = Math.max(0, tool.getDiameterMm() / 2.0);
        double depth = material.getThicknessMm();
        double feed = machine.getDefaultFeedMmMin();
        double plungeFeed = feed / 2.0;
        double step = Math.max(0.1, Math.min(tool.getMaxDepthMm(), depth));
        int passes = Math.max(1, (int) Math.ceil(depth / step));
        double safeZ = safeZ(machine);
        boolean useTabs = options.getTabsEnabled() != null ? options.getTabsEnabled() : machine.isTabsEnabled();
        boolean useFixings = options.getFixingsEnabled() != null ? options.getFixingsEnabled() : machine.isFixingsEnabled();
        double tabH = machine.getTabHeightMm() > 0 ? machine.getTabHeightMm() : 1.5;

        ToolpathResult result = new ToolpathResult();
        result.setSheetWidth(nest.getSheetWidth());
        result.setSheetHeight(nest.getSheetHeight());
        result.setSheetCount(nest.getSheetCount());
        result.setToolDiameterMm(tool.getDiameterMm());
        result.setToolName(tool.getName());
        result.setFixingHoleDiameterMm(machine.getFixingHoleDiameterMm() > 0 ? machine.getFixingHoleDiameterMm() : 4);
        result.setFixingMinToolDistanceMm(machine.getFixingMinToolDistanceMm() > 0 ? machine.getFixingMinToolDistanceMm() : 10);
        result.setTabWidthMm(machine.getTabWidthMm() > 0 ? machine.getTabWidthMm() : 5);
        result.setTabHeightMm(tabH);
        result.setTabsEnabled(useTabs);
        result.setFixingsEnabled(useFixings);

        // Build world outers for planners
        List<List<Vec2>> outerWorlds = new ArrayList<>();
        List<List<TabPlanner.TabSpec>> tabsPerPlacement = new ArrayList<>();
        int i = 0;
        for (NestPlacement pl : nest.getPlacements()) {
            GeometryModel model = models.get(Math.min(i, models.size() - 1));
            double[] origin = bboxMin(model);
            List<Vec2> outerLocal = null;
            for (PathMetrics.CutContour cc : PathMetrics.orderedContours(model, radius)) {
                if (!cc.hole()) { outerLocal = cc.points(); break; }
            }
            List<Vec2> outerWorld = new ArrayList<>();
            if (outerLocal != null) {
                for (Vec2 p : outerLocal) outerWorld.add(transform(p, pl, origin));
            }
            outerWorlds.add(outerWorld);
            if (useTabs && outerWorld.size() >= 3) {
                tabsPerPlacement.add(TabPlanner.plan(outerWorld, pl.getSheetIndex(), i, machine));
            } else {
                tabsPerPlacement.add(List.of());
            }
            i++;
        }

        // Flatten tabs for UI
        for (List<TabPlanner.TabSpec> ts : tabsPerPlacement) {
            for (TabPlanner.TabSpec t : ts) {
                List<ToolpathResult.Pt> seg = new ArrayList<>();
                for (Vec2 v : t.segment()) seg.add(new ToolpathResult.Pt(v.x(), v.y()));
                result.getTabs().add(new ToolpathResult.Tab(
                        t.id(), t.sheetIndex(), t.x(), t.y(), t.widthMm(), t.heightMm(), seg));
            }
        }

        // Fixings
        Set<String> disabled = options.disabledSet();
        List<FixingPlanner.Candidate> planned = useFixings
                ? FixingPlanner.plan(nest.getPlacements(), outerWorlds, machine, tool,
                nest.getSheetWidth(), nest.getSheetHeight(), nest.getMargin())
                : List.of();
        for (FixingPlanner.Candidate c : planned) {
            boolean en = !disabled.contains(c.id());
            result.getFixings().add(new ToolpathResult.Fixing(
                    c.id(), c.sheetIndex(), c.x(), c.y(), c.diameterMm(), en, c.label()));
        }
        List<FixingPlanner.Candidate> activeFixings = FixingPlanner.applyDisabled(planned, disabled);

        StringBuilder sb = new StringBuilder();
        sb.append("(SendIt LinuxCNC profile + fixings + tabs)\n");
        sb.append(String.format(Locale.US, "(Tool: %s Ø%.2f  offset=%.3f mm offline)\n",
                tool.getName(), tool.getDiameterMm(), radius));
        sb.append(String.format(Locale.US, "(Passes: %d x %.3f mm  depth=%.3f tabs=%s tabH=%.2f)\n",
                passes, step, depth, useTabs, tabH));
        sb.append(String.format(Locale.US, "(Fixings: %d active / %d planned Ø%.2f minDist=%.1f)\n",
                activeFixings.size(), planned.size(), result.getFixingHoleDiameterMm(),
                result.getFixingMinToolDistanceMm()));
        sb.append("G21 G90 G17\nG40 G49 G80\n");
        sb.append(String.format(Locale.US, "M6 T1\nS%.0f M3\n", machine.getDefaultSpeedRpm()));
        sb.append(String.format(Locale.US, "G0 Z%.3f\n", safeZ));

        // --- Drill fixings first ---
        if (!activeFixings.isEmpty()) {
            sb.append("(--- Screw fixings / hold-downs ---)\n");
            double drillDepth = depth + 0.5;
            double drillFeed = Math.min(plungeFeed, 400);
            for (FixingPlanner.Candidate f : activeFixings) {
                sb.append(String.format(Locale.US, "(Fixing %s)\n", f.id()));
                sb.append(String.format(Locale.US, "G0 X%.3f Y%.3f\n", f.x(), f.y()));
                sb.append(String.format(Locale.US, "G0 Z%.3f\n", safeZ));
                sb.append(String.format(Locale.US, "G1 Z%.3f F%.0f\n", -drillDepth, drillFeed));
                sb.append(String.format(Locale.US, "G0 Z%.3f\n", safeZ));
                // Viz: small cross / circle as drill
                List<ToolpathResult.Pt> cross = circlePts(f.x(), f.y(), f.diameterMm() / 2.0, 16);
                result.getPaths().add(new ToolpathResult.Path("drill", false, f.sheetIndex(), f.id(), cross));
            }
        }

        // --- Profile cuts ---
        i = 0;
        for (NestPlacement pl : nest.getPlacements()) {
            GeometryModel model = models.get(Math.min(i, models.size() - 1));
            double[] origin = bboxMin(model);
            String label = pl.getLabel() != null ? pl.getLabel() : ("Part" + i);
            List<TabPlanner.TabSpec> tabs = tabsPerPlacement.get(i);
            sb.append(String.format(Locale.US, "(Part %s sheet=%d rot=%.0f)\n",
                    label, pl.getSheetIndex(), pl.getRotationDeg()));
            for (PathMetrics.CutContour cc : PathMetrics.orderedContours(model, radius)) {
                List<Vec2> world = new ArrayList<>();
                for (Vec2 p : cc.points()) world.add(transform(p, pl, origin));
                if (cc.hole() || !useTabs || tabs.isEmpty()) {
                    writeContourFull(sb, result, world, pl, passes, step, depth, feed, plungeFeed,
                            safeZ, cc.hole(), label);
                } else {
                    writeOuterWithTabs(sb, result, world, pl, passes, step, depth, feed, plungeFeed,
                            safeZ, label, tabs, tabH);
                }
            }
            i++;
        }
        sb.append(String.format(Locale.US, "G0 Z%.3f\nM5\nM30\n", safeZ));
        result.setGcode(sb.toString());
        log.info("Generated G-code chars={} paths={} fixings={} tabs={}",
                sb.length(), result.getPaths().size(), result.getFixings().size(), result.getTabs().size());
        return result;
    }

    private void writeContourFull(StringBuilder sb, ToolpathResult result, List<Vec2> world,
                                  NestPlacement pl, int passes, double step, double depth,
                                  double feed, double plungeFeed, double safeZ, boolean hole, String label) {
        if (world.size() < 2) return;
        List<ToolpathResult.Pt> pts = toPts(world, true);
        Vec2 first = world.get(0);
        result.getPaths().add(new ToolpathResult.Path("rapid", hole, pl.getSheetIndex(), label,
                List.of(new ToolpathResult.Pt(first.x(), first.y()))));
        result.getPaths().add(new ToolpathResult.Path("cut", hole, pl.getSheetIndex(), label, pts));
        sb.append(String.format(Locale.US, "(%s)\n", hole ? "hole" : "outer"));
        sb.append(String.format(Locale.US, "G0 X%.3f Y%.3f\n", first.x(), first.y()));
        for (int p = 1; p <= passes; p++) {
            double z = -Math.min(depth, p * step);
            sb.append(String.format(Locale.US, "G1 Z%.3f F%.0f\n", z, plungeFeed));
            for (int i = 1; i < world.size(); i++) {
                Vec2 v = world.get(i);
                sb.append(String.format(Locale.US, "G1 X%.3f Y%.3f F%.0f\n", v.x(), v.y(), feed));
            }
            sb.append(String.format(Locale.US, "G1 X%.3f Y%.3f F%.0f\n", first.x(), first.y(), feed));
        }
        sb.append(String.format(Locale.US, "G0 Z%.3f\n", safeZ));
    }

    /**
     * Outer profile with tabs: shallow passes full loop; final thickness leaves bridges at tabs.
     */
    private void writeOuterWithTabs(StringBuilder sb, ToolpathResult result, List<Vec2> world,
                                    NestPlacement pl, int passes, double step, double depth,
                                    double feed, double plungeFeed, double safeZ, String label,
                                    List<TabPlanner.TabSpec> tabs, double tabH) {
        if (world.size() < 2) return;
        double perim = TabPlanner.perimeter(world);
        List<ToolpathResult.Pt> pts = toPts(world, true);
        Vec2 first = world.get(0);
        result.getPaths().add(new ToolpathResult.Path("rapid", false, pl.getSheetIndex(), label,
                List.of(new ToolpathResult.Pt(first.x(), first.y()))));
        result.getPaths().add(new ToolpathResult.Path("cut", false, pl.getSheetIndex(), label, pts));

        sb.append("(outer with tabs)\n");
        sb.append(String.format(Locale.US, "G0 X%.3f Y%.3f\n", first.x(), first.y()));

        double maxCut = Math.max(0.1, depth - tabH);
        for (int p = 1; p <= passes; p++) {
            double zTarget = -Math.min(depth, p * step);
            boolean finalPass = zTarget <= -maxCut - 1e-6 || p == passes;
            if (!finalPass || tabH <= 0) {
                // Full loop to this Z (or to maxCut on near-final)
                double z = finalPass ? -maxCut : zTarget;
                z = Math.max(z, -depth);
                sb.append(String.format(Locale.US, "G1 Z%.3f F%.0f\n", z, plungeFeed));
                writeLoop(sb, world, first, feed);
            } else {
                // Final full-depth cut with tab skips
                sb.append(String.format(Locale.US, "G1 Z%.3f F%.0f\n", zTarget, plungeFeed));
                writeLoopWithTabs(sb, world, first, feed, perim, tabs, safeZ, plungeFeed, zTarget);
            }
        }
        sb.append(String.format(Locale.US, "G0 Z%.3f\n", safeZ));
    }

    private void writeLoop(StringBuilder sb, List<Vec2> world, Vec2 first, double feed) {
        for (int i = 1; i < world.size(); i++) {
            Vec2 v = world.get(i);
            sb.append(String.format(Locale.US, "G1 X%.3f Y%.3f F%.0f\n", v.x(), v.y(), feed));
        }
        sb.append(String.format(Locale.US, "G1 X%.3f Y%.3f F%.0f\n", first.x(), first.y(), feed));
    }

    private void writeLoopWithTabs(StringBuilder sb, List<Vec2> world, Vec2 first, double feed,
                                   double perim, List<TabPlanner.TabSpec> tabs,
                                   double safeZ, double plungeFeed, double zCut) {
        double s = 0;
        boolean cutting = true;
        for (int i = 0; i < world.size(); i++) {
            Vec2 a = world.get(i);
            Vec2 b = world.get((i + 1) % world.size());
            if (i == world.size() - 1) b = first;
            double seg = a.dist(b);
            double mid = s + seg / 2;
            boolean tab = TabPlanner.inTab(mid, perim, tabs);
            if (tab && cutting) {
                sb.append(String.format(Locale.US, "G0 Z%.3f\n", safeZ));
                cutting = false;
            } else if (!tab && !cutting) {
                sb.append(String.format(Locale.US, "G0 X%.3f Y%.3f\n", a.x(), a.y()));
                sb.append(String.format(Locale.US, "G1 Z%.3f F%.0f\n", zCut, plungeFeed));
                cutting = true;
            }
            if (cutting) {
                sb.append(String.format(Locale.US, "G1 X%.3f Y%.3f F%.0f\n", b.x(), b.y(), feed));
            } else {
                sb.append(String.format(Locale.US, "G0 X%.3f Y%.3f\n", b.x(), b.y()));
            }
            s += seg;
        }
        if (!cutting) {
            sb.append(String.format(Locale.US, "G1 Z%.3f F%.0f\n", zCut, plungeFeed));
        }
    }

    private static List<ToolpathResult.Pt> toPts(List<Vec2> world, boolean close) {
        List<ToolpathResult.Pt> pts = new ArrayList<>();
        for (Vec2 v : world) pts.add(new ToolpathResult.Pt(v.x(), v.y()));
        if (close && pts.size() > 1) {
            ToolpathResult.Pt f = pts.get(0), l = pts.get(pts.size() - 1);
            if (Math.hypot(f.x() - l.x(), f.y() - l.y()) > 1e-4) pts.add(f);
        }
        return pts;
    }

    private static List<ToolpathResult.Pt> circlePts(double cx, double cy, double r, int n) {
        List<ToolpathResult.Pt> pts = new ArrayList<>();
        for (int i = 0; i <= n; i++) {
            double a = Math.PI * 2 * i / n;
            pts.add(new ToolpathResult.Pt(cx + r * Math.cos(a), cy + r * Math.sin(a)));
        }
        return pts;
    }

    Vec2 transform(Vec2 p, NestPlacement pl, double[] origin) {
        double nw = pl.getNativeWidth() > 0 ? pl.getNativeWidth() : pl.getWidth();
        double nh = pl.getNativeHeight() > 0 ? pl.getNativeHeight() : pl.getHeight();
        double[] box = NestMath.aabb(nw, nh, pl.getRotationDeg());
        double lx = p.x() - origin[0], ly = p.y() - origin[1];
        double rad = Math.toRadians(pl.getRotationDeg());
        double c = Math.cos(rad), s = Math.sin(rad);
        double dx = lx - nw / 2, dy = ly - nh / 2;
        double rx = dx * c - dy * s;
        double ry = dx * s + dy * c;
        return new Vec2(rx + pl.getX() + box[0] / 2, ry + pl.getY() + box[1] / 2);
    }

    static double safeZ(Machine machine) {
        double z = machine.getWorkZmm() > 0 ? Math.min(15.0, machine.getWorkZmm()) : 15.0;
        return Math.max(5.0, z);
    }

    private double[] bboxMin(GeometryModel model) {
        double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY;
        for (var c : model.getContours()) {
            for (Vec2 p : c.getPoints()) {
                minX = Math.min(minX, p.x());
                minY = Math.min(minY, p.y());
            }
        }
        if (!Double.isFinite(minX)) return new double[]{0, 0};
        return new double[]{minX, minY};
    }
}
