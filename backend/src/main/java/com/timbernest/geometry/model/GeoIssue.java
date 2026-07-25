package com.timbernest.geometry.model;

import java.util.List;

public record GeoIssue(
        String id,
        String category,
        String severity,
        String message,
        String contourId,
        List<Vec2> highlight,
        boolean autoFixable
) {}
