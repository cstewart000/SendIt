package com.timbernest.nesting;

import java.util.List;

/** Grid + pair-pocket search and gravity settle for NestBlf. */
final class NestSearch {
    private NestSearch() {}

    static NestBlf.Pose best(NestBlf.Piece piece, List<NestBlf.Placed> placed,
                             double margin, double uw, double uh, double gap) {
        NestBlf.Pose best = null;
        double bestScore = Double.POSITIVE_INFINITY;
        for (double rot : piece.rots()) {
            double[] box = NestMath.aabb(piece.part().getWidthMm(), piece.part().getHeightMm(), rot);
            double w = box[0], h = box[1];
            if (w > uw - margin || h > uh - margin) continue;
            double bias = (Math.abs(rot % 180) < 1e-6) ? 0 : w * h * 0.08;
            double step = Math.max(5, Math.min(w, h) / 22);
            for (double y = margin; y + h <= uh + 1e-6; y += step) {
                for (double x = margin; x + w <= uw + 1e-6; x += step) {
                    double s = score(piece, x, y, rot, w, h, placed, margin, uw, uh, gap, bias);
                    if (s < bestScore) { bestScore = s; best = new NestBlf.Pose(x, y, rot, w, h); }
                }
            }
            NestBlf.Pose pocket = pocketBest(piece, rot, w, h, placed, margin, uw, uh, gap, bias);
            if (pocket != null) {
                double s = score(piece, pocket.x(), pocket.y(), rot, w, h, placed, margin, uw, uh,
                        gap, bias);
                if (s < bestScore) { bestScore = s; best = pocket; }
            }
        }
        return best;
    }

    static NestBlf.Pose settle(NestBlf.Piece piece, NestBlf.Pose pose, List<NestBlf.Placed> placed,
                               double margin, double uw, double uh, double gap) {
        double x = pose.x(), y = pose.y(), step = Math.max(1, Math.min(pose.w(), pose.h()) / 50);
        boolean moved = true;
        while (moved) {
            moved = false;
            if (NestBlf.fits(piece, x, y - step, pose.rot(), pose.w(), pose.h(),
                    placed, margin, uw, uh, gap)) { y -= step; moved = true; }
            else if (NestBlf.fits(piece, x - step, y, pose.rot(), pose.w(), pose.h(),
                    placed, margin, uw, uh, gap)) { x -= step; moved = true; }
        }
        return new NestBlf.Pose(x, y, pose.rot(), pose.w(), pose.h());
    }

    private static NestBlf.Pose pocketBest(NestBlf.Piece piece, double rot, double w, double h,
                                           List<NestBlf.Placed> placed, double margin, double uw,
                                           double uh, double gap, double bias) {
        NestBlf.Pose best = null;
        double bestScore = Double.POSITIVE_INFINITY;
        double fine = Math.max(2, Math.min(w, h) / 40);
        for (NestBlf.Placed p : placed) {
            double[][] seeds = {
                    {p.x1() + gap, p.y0()}, {p.x0(), p.y1() + gap},
                    {p.x1() - w * 0.45, p.y0() - h * 0.25},
                    {p.x0() - w * 0.25, p.y1() - h * 0.45},
                    {p.x0() + (p.x1() - p.x0()) * 0.5 - w * 0.5, p.y1() + gap},
                    {p.x0() + w * 0.55, p.y0() - h * 0.15},
                    {p.x0() - w * 0.15, p.y0() + h * 0.55},
            };
            for (double[] s0 : seeds) {
                for (double y = s0[1] - h * 0.55; y <= s0[1] + h * 0.55; y += fine) {
                    for (double x = s0[0] - w * 0.55; x <= s0[0] + w * 0.55; x += fine) {
                        double s = score(piece, x, y, rot, w, h, placed, margin, uw, uh, gap, bias);
                        if (s < bestScore) {
                            bestScore = s;
                            best = new NestBlf.Pose(x, y, rot, w, h);
                        }
                    }
                }
            }
        }
        return best;
    }

    private static double score(NestBlf.Piece piece, double x, double y, double rot, double w,
                                double h, List<NestBlf.Placed> placed, double margin, double uw,
                                double uh, double gap, double bias) {
        if (!NestBlf.fits(piece, x, y, rot, w, h, placed, margin, uw, uh, gap))
            return Double.POSITIVE_INFINITY;
        return NestScore.rank(NestScore.union(placed, x, y, w, h), x, y) + bias;
    }
}
