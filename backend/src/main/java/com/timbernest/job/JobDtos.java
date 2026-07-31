package com.timbernest.job;

import com.timbernest.common.JobStatus;
import com.timbernest.nesting.NestPlacement;

import java.util.List;
import java.util.Map;

public final class JobDtos {
    private JobDtos() {}

    public record CreateJobRequest(String title, Long machineId, Long materialId, Long toolId,
                                   Double marginMm, Double partGapMm) {}

    public record UpdateJobRequest(String title) {}

    public record PartQty(Long partId, Integer quantity) {}

    public record AddPartRequest(Long designVersionId, List<Long> partIds,
                                 List<PartQty> quantities, Integer quantity,
                                 String label, Boolean grainSensitive) {}

    public record QtyUpdate(Long jobPartId, Integer quantity) {}

    public record UpdateQuantitiesRequest(List<QtyUpdate> updates, Integer allQuantity) {}

    public record AdjustNestRequest(List<NestPlacement> placements) {}

    public record Pt(double x, double y) {}

    public record ContourPath(boolean closed, List<Pt> points) {}

    /** Part outline for nest preview (points relative to bbox min / native origin). */
    public record NestShape(Long jobPartId, double nativeWidth, double nativeHeight,
                            List<ContourPath> contours) {}

    public record JobSummary(int partCount, int sheetCount, double partsAreaMm2,
                             Double cycleMinutes, Double cost, String currency) {}

    public record JobView(Long id, String title, JobStatus status, Long machineId, Long materialId,
                          Long toolId, boolean nestingLocked, double marginMm, double partGapMm,
                          List<JobPart> parts, Map<String, Object> nesting, Map<String, Object> quote,
                          boolean hasGcode, JobSummary summary) {}
}
