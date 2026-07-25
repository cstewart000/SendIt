package com.timbernest.nesting;

import com.timbernest.job.JobPart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

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

        List<JobPart> expanded = new ArrayList<>();
        for (JobPart p : parts) {
            for (int q = 0; q < p.getQuantity(); q++) expanded.add(p);
        }
        expanded.sort(Comparator.comparingDouble((JobPart p) -> p.getWidthMm() * p.getHeightMm()).reversed());

        int sheet = 0;
        double cursorX = margin, cursorY = margin, rowH = 0;
        double usableW = sheetW - margin, usableH = sheetH - margin;

        for (JobPart part : expanded) {
            double w = part.getWidthMm(), h = part.getHeightMm(), rot = 0;
            if (!part.isGrainSensitive() && h > w && w <= usableW - margin) {
                double t = w; w = h; h = t; rot = 90;
            }
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
            NestPlacement pl = new NestPlacement();
            pl.setJobPartId(part.getId());
            pl.setLabel(part.getLabel());
            pl.setSheetIndex(sheet);
            pl.setX(cursorX);
            pl.setY(cursorY);
            pl.setWidth(w);
            pl.setHeight(h);
            pl.setRotationDeg(rot);
            pl.setGrainSensitive(part.isGrainSensitive());
            result.getPlacements().add(pl);
            cursorX += w + gap;
            rowH = Math.max(rowH, h);
        }
        result.setSheetCount(result.getPlacements().isEmpty() ? 0 : sheet + 1);
        log.info("Nested {} instances onto {} sheets", result.getPlacements().size(), result.getSheetCount());
        return result;
    }
}
