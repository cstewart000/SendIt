package com.timbernest.nesting;

import com.timbernest.job.JobPart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class NestingService {
    private static final Logger log = LoggerFactory.getLogger(NestingService.class);

    public NestResult nest(List<JobPart> parts, double sheetW, double sheetH,
                           double margin, double gap) {
        NestResult result = new NestResult();
        result.setSheetWidth(sheetW);
        result.setSheetHeight(sheetH);
        result.setMargin(margin);
        result.setGap(gap);

        double usableW = sheetW - margin;
        // One orientation per job part type (shared across all instances).
        Map<Long, Double> orient = new HashMap<>();
        for (JobPart part : parts) {
            double w = part.getWidthMm(), h = part.getHeightMm(), rot = 0;
            if (!part.isGrainSensitive() && h > w && h <= usableW - margin) rot = 90;
            orient.put(part.getId(), rot);
        }

        List<JobPart> expanded = new ArrayList<>();
        for (JobPart p : parts) {
            for (int q = 0; q < p.getQuantity(); q++) expanded.add(p);
        }
        expanded.sort(Comparator.comparingDouble((JobPart p) -> p.getWidthMm() * p.getHeightMm()).reversed());

        int sheet = 0;
        double cursorX = margin, cursorY = margin, rowH = 0;
        double usableH = sheetH - margin;

        for (JobPart part : expanded) {
            double rot = orient.getOrDefault(part.getId(), 0.0);
            NestPlacement pl = new NestPlacement();
            pl.setJobPartId(part.getId());
            pl.setLabel(part.getLabel());
            pl.setSheetIndex(sheet);
            pl.setGrainSensitive(part.isGrainSensitive());
            NestMath.applyOrientation(pl, rot, part.getWidthMm(), part.getHeightMm());
            double w = pl.getWidth(), h = pl.getHeight();
            if (cursorX + w > usableW) {
                cursorX = margin;
                cursorY += rowH + gap;
                rowH = 0;
            }
            if (cursorY + h > usableH) {
                sheet++;
                cursorX = margin;
                cursorY = margin;
                rowH = 0;
            }
            pl.setX(cursorX);
            pl.setY(cursorY);
            result.getPlacements().add(pl);
            cursorX += w + gap;
            rowH = Math.max(rowH, h);
        }
        result.setSheetCount(result.getPlacements().isEmpty() ? 0 : sheet + 1);
        log.info("Nested {} instances onto {} sheets (shared orientations)", result.getPlacements().size(),
                result.getSheetCount());
        return result;
    }

    /** Force all instances of each jobPartId to share one rotation. */
    public void syncSharedOrientations(List<NestPlacement> placements, Map<Long, JobPart> byId) {
        Map<Long, Double> chosen = new LinkedHashMap<>();
        for (NestPlacement pl : placements) {
            if (pl.getJobPartId() == null) continue;
            chosen.putIfAbsent(pl.getJobPartId(), pl.getRotationDeg());
        }
        for (NestPlacement pl : placements) {
            Long id = pl.getJobPartId();
            if (id == null) continue;
            JobPart part = byId.get(id);
            double nw = pl.getNativeWidth() > 0 ? pl.getNativeWidth()
                    : (part != null ? part.getWidthMm() : pl.getWidth());
            double nh = pl.getNativeHeight() > 0 ? pl.getNativeHeight()
                    : (part != null ? part.getHeightMm() : pl.getHeight());
            if (part != null) pl.setGrainSensitive(part.isGrainSensitive());
            NestMath.applyOrientation(pl, chosen.getOrDefault(id, 0.0), nw, nh);
        }
        log.info("Synced orientations for {} part types", chosen.size());
    }
}
