package com.timbernest.nesting;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Shared AABB / orientation helpers for nest placements. */
public final class NestMath {
    private static final Logger log = LoggerFactory.getLogger(NestMath.class);
    private NestMath() {}

    public static double[] aabb(double w, double h, double deg) {
        double r = Math.toRadians(deg);
        double c = Math.abs(Math.cos(r)), s = Math.abs(Math.sin(r));
        return new double[]{w * c + h * s, w * s + h * c};
    }

    /** Grain-sensitive parts stay axis-aligned: 0° (L→R) or 90° (U→D). */
    public static double constrain(double deg, boolean grainSensitive) {
        double n = ((deg % 360) + 360) % 360;
        if (!grainSensitive) return n;
        int q = (int) Math.round(n / 90.0) % 4;
        if (q < 0) q += 4;
        double snapped = (q == 1 || q == 3) ? 90 : 0;
        log.debug("Grain constrain {} -> {}", deg, snapped);
        return snapped;
    }

    public static void applyOrientation(NestPlacement pl, double deg, double nativeW, double nativeH) {
        double rot = constrain(deg, pl.isGrainSensitive());
        double[] box = aabb(nativeW, nativeH, rot);
        pl.setRotationDeg(rot);
        pl.setNativeWidth(nativeW);
        pl.setNativeHeight(nativeH);
        pl.setWidth(box[0]);
        pl.setHeight(box[1]);
    }
}
