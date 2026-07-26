package com.timbernest.job;

import com.timbernest.common.JobStatus;
import com.timbernest.nesting.NestPlacement;

import java.util.List;
import java.util.Map;

public final class JobDtos {
    private JobDtos() {}

    public record CreateJobRequest(Long machineId, Long materialId, Long toolId,
                                   Double marginMm, Double partGapMm) {}

    public record AddPartRequest(Long designVersionId, List<Long> partIds, String label,
                                 Integer quantity, Boolean grainSensitive) {}

    public record AdjustNestRequest(List<NestPlacement> placements) {}

    public record JobView(Long id, JobStatus status, Long machineId, Long materialId, Long toolId,
                          boolean nestingLocked, double marginMm, double partGapMm,
                          List<JobPart> parts, Map<String, Object> nesting, Map<String, Object> quote,
                          boolean hasGcode) {}
}
