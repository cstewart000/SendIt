package com.timbernest.nesting;

import java.util.ArrayList;
import java.util.List;

/** Places a complementary 0°/180° pair as one rigid block. */
final class NestPairPlace {
    private NestPairPlace() {}

    record PairPose(NestBlf.Pose a, NestBlf.Pose b) {}

    static PairPose best(NestBlf.Piece pa, NestBlf.Piece pb, List<NestBlf.Placed> placed,
                         double margin, double uw, double uh, double gap) {
        NestPair.Layout lay = NestPair.bestLayout(pa.local(), pa.part().getWidthMm(),
                pa.part().getHeightMm(), gap);
        double[] boxA = NestMath.aabb(pa.part().getWidthMm(), pa.part().getHeightMm(), lay.a().rot());
        double[] boxB = NestMath.aabb(pb.part().getWidthMm(), pb.part().getHeightMm(), lay.b().rot());
        double bw = Math.max(lay.a().x() + boxA[0], lay.b().x() + boxB[0]);
        double bh = Math.max(lay.a().y() + boxA[1], lay.b().y() + boxB[1]);
        PairPose best = null;
        double bestScore = Double.POSITIVE_INFINITY;
        double step = Math.max(5, Math.min(bw, bh) / 20);
        for (double y = margin; y + bh <= uh + 1e-6; y += step) {
            for (double x = margin; x + bw <= uw + 1e-6; x += step) {
                NestBlf.Pose a = abs(lay.a(), boxA, x, y);
                NestBlf.Pose b = abs(lay.b(), boxB, x, y);
                if (!NestBlf.fits(pa, a.x(), a.y(), a.rot(), a.w(), a.h(),
                        placed, margin, uw, uh, gap)) continue;
                NestBlf.Placed placedA = NestBlf.asPlaced(pa, a);
                List<NestBlf.Placed> tmp = new ArrayList<>(placed);
                tmp.add(placedA);
                if (!NestBlf.fits(pb, b.x(), b.y(), b.rot(), b.w(), b.h(),
                        tmp, margin, uw, uh, gap)) continue;
                NestScore.Box u = NestScore.union(tmp, b.x(), b.y(), b.w(), b.h());
                double s = NestScore.rank(u, Math.min(a.x(), b.x()), Math.min(a.y(), b.y()));
                if (s < bestScore) { bestScore = s; best = new PairPose(a, b); }
            }
        }
        return best;
    }

    private static NestBlf.Pose abs(NestPair.Pose rel, double[] box, double ox, double oy) {
        return new NestBlf.Pose(ox + rel.x(), oy + rel.y(), rel.rot(), box[0], box[1]);
    }
}
