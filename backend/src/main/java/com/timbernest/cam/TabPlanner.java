package com.timbernest.cam;

import com.timbernest.admin.Machine;
import com.timbernest.geometry.model.Vec2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/** Places hold-down tabs along outer profile toolpaths. */
public final class TabPlanner {
    private static final Logger log = LoggerFactory.getLogger(TabPlanner.class);
    private TabPlanner() {}

    public record TabSpec(String id, int sheetIndex, double x, double y,
                          double widthMm, double heightMm, double alongStart, double alongEnd,
                          List<Vec2> segment) {}

    /**
     * @param outerWorld outer toolpath (closed) in sheet coords
     * @return tabs with arc-length windows along the path
     */
    public static List<TabSpec> plan(List<Vec2> outerWorld, int sheetIndex, int placementIndex,
                                     Machine machine) {
        if (!machine.isTabsEnabled() || outerWorld == null || outerWorld.size() < 3) return List.of();
        double tabW = machine.getTabWidthMm() > 0 ? machine.getTabWidthMm() : 5;
        double tabH = machine.getTabHeightMm() > 0 ? machine.getTabHeightMm() : 1.5;
        int want = Math.max(2, machine.getTabCount() > 0 ? machine.getTabCount() : 4);

        double perim = perimeter(outerWorld);
        if (perim < tabW * want * 2.5) {
            want = Math.max(2, (int) (perim / (tabW * 3)));
        }
        if (want < 1 || perim < tabW * 2) return List.of();

        List<TabSpec> tabs = new ArrayList<>();
        // Evenly spaced, avoiding starting vertex clustering
        double spacing = perim / want;
        for (int t = 0; t < want; t++) {
            double mid = spacing * (t + 0.5);
            double start = mid - tabW / 2;
            double end = mid + tabW / 2;
            if (start < 0) { start += perim; end += perim; }
            Vec2 center = pointAt(outerWorld, mid % perim);
            List<Vec2> seg = segmentBetween(outerWorld, start % perim, end % perim, perim);
            tabs.add(new TabSpec(
                    "tab-" + placementIndex + "-" + t,
                    sheetIndex,
                    center.x(), center.y(),
                    tabW, tabH,
                    start % perim, end % perim,
                    seg
            ));
        }
        log.debug("Tabs for placement {}: {}", placementIndex, tabs.size());
        return tabs;
    }

    /** True if arc-length s (from path start) falls inside any tab window. */
    public static boolean inTab(double s, double perim, List<TabSpec> tabs) {
        if (tabs == null || tabs.isEmpty()) return false;
        double ss = ((s % perim) + perim) % perim;
        for (TabSpec t : tabs) {
            double a = t.alongStart(), b = t.alongEnd();
            if (a <= b) {
                if (ss >= a && ss <= b) return true;
            } else {
                // wraps
                if (ss >= a || ss <= b) return true;
            }
        }
        return false;
    }

    static double perimeter(List<Vec2> poly) {
        double len = 0;
        for (int i = 0; i < poly.size(); i++) {
            len += poly.get(i).dist(poly.get((i + 1) % poly.size()));
        }
        return len;
    }

    static Vec2 pointAt(List<Vec2> poly, double s) {
        double rem = s;
        for (int i = 0; i < poly.size(); i++) {
            Vec2 a = poly.get(i), b = poly.get((i + 1) % poly.size());
            double seg = a.dist(b);
            if (rem <= seg || i == poly.size() - 1) {
                double t = seg < 1e-9 ? 0 : rem / seg;
                return new Vec2(a.x() + (b.x() - a.x()) * t, a.y() + (b.y() - a.y()) * t);
            }
            rem -= seg;
        }
        return poly.get(0);
    }

    static List<Vec2> segmentBetween(List<Vec2> poly, double s0, double s1, double perim) {
        List<Vec2> out = new ArrayList<>();
        out.add(pointAt(poly, s0));
        // sample a few points
        double span = s1 >= s0 ? s1 - s0 : (perim - s0 + s1);
        int n = Math.max(2, (int) Math.ceil(span / 2));
        for (int i = 1; i <= n; i++) {
            double s = (s0 + span * i / n) % perim;
            out.add(pointAt(poly, s));
        }
        return out;
    }
}
