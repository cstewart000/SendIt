package com.timbernest.design;

import com.timbernest.geometry.model.GeoIssue;
import com.timbernest.geometry.model.GeometryModel;

import java.time.Instant;
import java.util.List;

public final class DesignDtos {
    private DesignDtos() {}

    public record DesignSummary(Long id, String name, Instant updatedAt, int latestVersion) {}

    public record VersionDto(Long id, int versionNumber, String originalFilename, boolean analysed,
                             boolean repaired, boolean warningsAcknowledged, Instant createdAt,
                             GeometryModel geometry, List<GeoIssue> issues) {}

    public record DesignDetail(Long id, String name, List<VersionDto> versions) {}

    public record RepairRequest(boolean confirm) {}

    public record AckRequest(boolean acknowledge) {}
}
