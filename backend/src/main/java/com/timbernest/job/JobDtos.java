package com.timbernest.job;

import com.timbernest.common.JobStatus;
import com.timbernest.nesting.NestPlacement;

import java.util.List;
import java.util.Map;

public final class JobDtos {
    private JobDtos() {}

    public record CreateJobRequest(Long machineId, Long materialId, Long toolId,
                                   Double marginMm, Double partGapMm) {}

    public record PartQty(Long partId, Integer quantity) {}

    public record AddPartRequest(Long designVersionId, List<Long> partIds,
                                 List<PartQty> quantities, Integer quantity,
                                 String label, Boolean grainSensitive) {}

    public record QtyUpdate(Long jobPartId, Integer quantity) {}

    public record UpdateQuantitiesRequest(List<QtyUpdate> updates, Integer allQuantity) {}

    public record AdjustNestRequest(List<NestPlacement> placements) {}

    public record JobView(Long id, JobStatus status, Long machineId, Long materialId, Long toolId,
                          boolean nestingLocked, double marginMm, double partGapMm,
                          List<JobPart> parts, Map<String, Object> nesting, Map<String, Object> quote,
                          boolean hasGcode) {}
}
