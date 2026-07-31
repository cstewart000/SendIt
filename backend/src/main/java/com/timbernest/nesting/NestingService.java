package com.timbernest.nesting;

import com.timbernest.common.ApiException;
import com.timbernest.geometry.model.GeometryModel;
import com.timbernest.geometry.model.Vec2;
import com.timbernest.job.JobPart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

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

        List<NestBlf.Piece> pieces = new ArrayList<>();
        Map<Long, Integer> seen = new HashMap<>();
        for (JobPart part : parts) {
            GeometryModel geo = geos.get(part.getId());
            List<Vec2> local = geo != null ? NestPoly.outerLocal(geo) : List.of();
            if (local.size() < 3 && part.getWidthMm() > 0 && part.getHeightMm() > 0) {
                // AABB fallback so rectangular metadata-only parts still nest
                local = List.of(
                        new Vec2(0, 0),
                        new Vec2(part.getWidthMm(), 0),
                        new Vec2(part.getWidthMm(), part.getHeightMm()),
                        new Vec2(0, part.getHeightMm()));
            }
            for (int i = 0; i < part.getQuantity(); i++) {
                int n = seen.merge(part.getId(), 1, Integer::sum);
                pieces.add(new NestBlf.Piece(part, local, rotations(part, n)));
            }
        }
        pieces.sort(Comparator.comparingDouble(
                (NestBlf.Piece p) -> p.part().getWidthMm() * p.part().getHeightMm()).reversed());

        NestBlf.PackResult pack = NestBlf.pack(pieces, sheetW, sheetH, margin, gap);
        if (!pack.complete()) {
            String labels = pack.unplaced().stream().distinct().collect(Collectors.joining(", "));
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Nesting incomplete — could not place: " + labels
                            + " (check part size vs sheet " + Math.round(sheetW) + "×"
                            + Math.round(sheetH) + " mm, margin " + margin + " mm)");
        }
        result.getPlacements().addAll(pack.placements());
        result.setSheetCount(pack.sheetCount());
        log.info("Nested {} pcs on {} sheets (BLF true-shape)", pack.placements().size(),
                result.getSheetCount());
        return result;
    }

    /**
     * Search 0/180 first for interlocking, then 90/270 as fallback.
     * Odd/even instances bias complementary flips.
     */
    private double[] rotations(JobPart part, int instance) {
        if (part.isGrainSensitive()) return new double[]{0, 90};
        boolean flip = instance % 2 == 0;
        return flip ? new double[]{0, 180, 90, 270} : new double[]{180, 0, 270, 90};
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
}
