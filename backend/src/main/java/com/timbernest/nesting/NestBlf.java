package com.timbernest.nesting;

import com.timbernest.geometry.model.Vec2;
import com.timbernest.job.JobPart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Pair-block + single true-shape packer with square/aspect scoring. */
public final class NestBlf {
    private static final Logger log = LoggerFactory.getLogger(NestBlf.class);
    private NestBlf() {}

    public record Piece(JobPart part, List<Vec2> local, double[] rots) {}
    public record Placed(List<Vec2> poly, double x0, double y0, double x1, double y1) {}
    public record Pose(double x, double y, double rot, double w, double h) {}

    /** Successful placements plus labels that could not fit any sheet. */
    public record PackResult(List<NestPlacement> placements, List<String> unplaced) {
        public boolean complete() { return unplaced.isEmpty(); }
        public int sheetCount() {
            return placements.stream().mapToInt(NestPlacement::getSheetIndex).max().orElse(-1) + 1;
        }
    }

    public static PackResult pack(List<Piece> pieces, double sheetW, double sheetH,
                                  double margin, double gap) {
        List<NestPlacement> out = new ArrayList<>();
        List<String> unplaced = new ArrayList<>();
        List<Placed> placed = new ArrayList<>();
        int sheet = 0;
        double uw = sheetW - margin, uh = sheetH - margin;
        int i = 0;
        while (i < pieces.size()) {
            Piece a = pieces.get(i);
            boolean paired = false;
            if (i + 1 < pieces.size() && canPair(a, pieces.get(i + 1))) {
                NestPairPlace.PairPose pp = NestPairPlace.best(a, pieces.get(i + 1),
                        placed, margin, uw, uh, gap);
                if (pp != null) {
                    commit(a, pp.a(), placed, out, sheet);
                    commit(pieces.get(i + 1), pp.b(), placed, out, sheet);
                    i += 2;
                    paired = true;
                }
            }
            if (paired) continue;
            Pose best = NestSearch.best(a, placed, margin, uw, uh, gap);
            if (best == null) {
                sheet++;
                placed.clear();
                if (i + 1 < pieces.size() && canPair(a, pieces.get(i + 1))) {
                    NestPairPlace.PairPose pp = NestPairPlace.best(a, pieces.get(i + 1),
                            placed, margin, uw, uh, gap);
                    if (pp != null) {
                        commit(a, pp.a(), placed, out, sheet);
                        commit(pieces.get(i + 1), pp.b(), placed, out, sheet);
                        i += 2;
                        continue;
                    }
                }
                best = NestSearch.best(a, placed, margin, uw, uh, gap);
            }
            if (best == null) {
                String label = a.part().getLabel() != null ? a.part().getLabel() : "part";
                log.warn("Could not place {}", label);
                unplaced.add(label);
                i++;
                continue;
            }
            best = NestSearch.settle(a, best, placed, margin, uw, uh, gap);
            commit(a, best, placed, out, sheet);
            i++;
        }
        NestScore.Box box = NestScore.of(placed);
        log.info("BLF packed {}/{} parts on {} sheet(s); footprint {}x{} (aspect {}); unplaced={}",
                out.size(), pieces.size(), out.isEmpty() ? 0 : sheet + 1,
                Math.round(box.w()), Math.round(box.h()),
                Math.round(box.aspect() * 10) / 10.0, unplaced.size());
        return new PackResult(out, List.copyOf(unplaced));
    }

    private static boolean canPair(Piece a, Piece b) {
        return !a.part().isGrainSensitive()
                && Objects.equals(a.part().getId(), b.part().getId())
                && a.local().size() >= 3;
    }

    private static void commit(Piece piece, Pose pose, List<Placed> placed,
                               List<NestPlacement> out, int sheet) {
        placed.add(asPlaced(piece, pose));
        out.add(toPlacement(piece.part(), pose, sheet));
    }

    static Placed asPlaced(Piece piece, Pose pose) {
        List<Vec2> poly = NestPoly.world(piece.local(), piece.part().getWidthMm(),
                piece.part().getHeightMm(), pose.x(), pose.y(), pose.rot());
        double[] b = NestPoly.bounds(poly);
        return new Placed(poly, b[0], b[1], b[2], b[3]);
    }

    static boolean fits(Piece piece, double x, double y, double rot, double w, double h,
                        List<Placed> placed, double margin, double uw, double uh, double gap) {
        if (x < margin - 1e-6 || y < margin - 1e-6 || x + w > uw + 1e-6 || y + h > uh + 1e-6)
            return false;
        List<Vec2> poly = NestPoly.world(piece.local(), piece.part().getWidthMm(),
                piece.part().getHeightMm(), x, y, rot);
        for (Placed p : placed) if (NestPoly.collide(poly, p.poly(), gap)) return false;
        return true;
    }

    private static NestPlacement toPlacement(JobPart part, Pose pose, int sheet) {
        NestPlacement pl = new NestPlacement();
        pl.setJobPartId(part.getId());
        pl.setLabel(part.getLabel());
        pl.setGrainSensitive(part.isGrainSensitive());
        pl.setSheetIndex(sheet);
        NestMath.applyOrientation(pl, pose.rot(), part.getWidthMm(), part.getHeightMm());
        pl.setX(pose.x());
        pl.setY(pose.y());
        return pl;
    }
}
