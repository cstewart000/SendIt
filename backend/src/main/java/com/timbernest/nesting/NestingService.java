package com.timbernest.nesting;

import com.timbernest.geometry.model.GeometryModel;
import com.timbernest.geometry.model.Vec2;
import com.timbernest.job.JobPart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class NestingService {
    private static final Logger log = LoggerFactory.getLogger(NestingService.class);

    public NestResult nest(List<JobPart> parts, Map<Long, GeometryModel> geos,
                           double sheetW, double sheetH, double margin, double gap) {
        NestResult result = new NestResult();
        result.setSheetWidth(sheetW);
        result.setSheetHeight(sheetH);
        result.setMargin(margin);
        result.setGap(gap);

        List<Unit> units = new ArrayList<>();
        for (JobPart part : parts) {
            GeometryModel geo = geos.get(part.getId());
            List<Vec2> local = geo != null ? NestPoly.outerLocal(geo) : List.of();
            double nw = part.getWidthMm(), nh = part.getHeightMm();
            int left = part.getQuantity();
            NestPair.Layout pair = null;
            if (!part.isGrainSensitive() && left >= 2 && local.size() >= 3) {
                pair = NestPair.bestLayout(local, nw, nh, gap);
                if (pair.area() >= (nw * 2 + gap) * nh - 1) pair = null; // not denser
            }
            while (pair != null && left >= 2) {
                units.add(Unit.pair(part, pair));
                left -= 2;
            }
            while (left-- > 0) units.add(Unit.single(part, pickRot(part, sheetW, margin)));
        }
        units.sort(Comparator.comparingDouble(Unit::area).reversed());

        int sheet = 0;
        double cursorX = margin, cursorY = margin, rowH = 0;
        double usableW = sheetW - margin, usableH = sheetH - margin;
        for (Unit u : units) {
            if (cursorX + u.w > usableW) {
                cursorX = margin;
                cursorY += rowH + gap;
                rowH = 0;
            }
            if (cursorY + u.h > usableH) {
                sheet++;
                cursorX = margin;
                cursorY = margin;
                rowH = 0;
            }
            for (NestPlacement pl : u.at(cursorX, cursorY, sheet)) result.getPlacements().add(pl);
            cursorX += u.w + gap;
            rowH = Math.max(rowH, u.h);
        }
        result.setSheetCount(result.getPlacements().isEmpty() ? 0 : sheet + 1);
        log.info("Nested {} pcs / {} units on {} sheets (pair-aware)",
                result.getPlacements().size(), units.size(), result.getSheetCount());
        return result;
    }

    /** Grain-only constraint; instances may differ (one-up/one-down). */
    public void syncSharedOrientations(List<NestPlacement> placements, Map<Long, JobPart> byId) {
        for (NestPlacement pl : placements) {
            JobPart part = pl.getJobPartId() != null ? byId.get(pl.getJobPartId()) : null;
            double nw = pl.getNativeWidth() > 0 ? pl.getNativeWidth()
                    : (part != null ? part.getWidthMm() : pl.getWidth());
            double nh = pl.getNativeHeight() > 0 ? pl.getNativeHeight()
                    : (part != null ? part.getHeightMm() : pl.getHeight());
            if (part != null) pl.setGrainSensitive(part.isGrainSensitive());
            NestMath.applyOrientation(pl, pl.getRotationDeg(), nw, nh);
        }
    }

    private double pickRot(JobPart part, double sheetW, double margin) {
        if (!part.isGrainSensitive() && part.getHeightMm() > part.getWidthMm()
                && part.getHeightMm() <= sheetW - 2 * margin) return 90;
        return 0;
    }

    private record Unit(double w, double h, List<NestPlacement> relative) {
        double area() { return w * h; }

        List<NestPlacement> at(double x, double y, int sheet) {
            List<NestPlacement> out = new ArrayList<>();
            for (NestPlacement r : relative) {
                NestPlacement pl = copy(r);
                pl.setX(r.getX() + x);
                pl.setY(r.getY() + y);
                pl.setSheetIndex(sheet);
                out.add(pl);
            }
            return out;
        }

        static Unit single(JobPart part, double rot) {
            NestPlacement pl = base(part);
            NestMath.applyOrientation(pl, rot, part.getWidthMm(), part.getHeightMm());
            pl.setX(0); pl.setY(0);
            return new Unit(pl.getWidth(), pl.getHeight(), List.of(pl));
        }

        static Unit pair(JobPart part, NestPair.Layout layout) {
            NestPlacement a = base(part), b = base(part);
            NestMath.applyOrientation(a, layout.a().rot(), part.getWidthMm(), part.getHeightMm());
            NestMath.applyOrientation(b, layout.b().rot(), part.getWidthMm(), part.getHeightMm());
            a.setX(layout.a().x()); a.setY(layout.a().y());
            b.setX(layout.b().x()); b.setY(layout.b().y());
            return new Unit(layout.width(), layout.height(), List.of(a, b));
        }

        static NestPlacement base(JobPart part) {
            NestPlacement pl = new NestPlacement();
            pl.setJobPartId(part.getId());
            pl.setLabel(part.getLabel());
            pl.setGrainSensitive(part.isGrainSensitive());
            return pl;
        }

        static NestPlacement copy(NestPlacement r) {
            NestPlacement pl = new NestPlacement();
            pl.setJobPartId(r.getJobPartId());
            pl.setLabel(r.getLabel());
            pl.setGrainSensitive(r.isGrainSensitive());
            pl.setNativeWidth(r.getNativeWidth());
            pl.setNativeHeight(r.getNativeHeight());
            pl.setRotationDeg(r.getRotationDeg());
            pl.setWidth(r.getWidth());
            pl.setHeight(r.getHeight());
            return pl;
        }
    }
}
