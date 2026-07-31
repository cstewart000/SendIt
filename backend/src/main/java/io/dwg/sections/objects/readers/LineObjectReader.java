package io.dwg.sections.objects.readers;

import io.dwg.core.io.BitStreamReader;
import io.dwg.core.type.Point3D;
import io.dwg.core.version.DwgVersion;
import io.dwg.entities.DwgObject;
import io.dwg.entities.DwgObjectType;
import io.dwg.entities.concrete.DwgLine;
import io.dwg.sections.objects.EntityHeaderReader;
import io.dwg.sections.objects.ObjectReader;

/**
 * Fixed LINE reader: R2000+ end coords are FIELD_DD (default-double vs start),
 * not raw doubles — the upstream jDwgParser bug dropped most straight edges.
 */
public class LineObjectReader implements ObjectReader {

    @Override
    public int objectType() { return DwgObjectType.LINE.typeCode(); }

    @Override
    public void read(DwgObject target, BitStreamReader r, DwgVersion v) throws Exception {
        EntityHeaderReader.readEntityHeader(r, v);
        EntityHeaderReader.readCommonEntityData(r, v);

        DwgLine line = (DwgLine) target;
        if (v.until(DwgVersion.R14)) {
            double[] start = r.read3BitDouble();
            double[] end = r.read3BitDouble();
            line.setStart(new Point3D(start[0], start[1], start[2]));
            line.setEnd(new Point3D(end[0], end[1], end[2]));
        } else {
            // LibreDWG dwg.spec SINCE(R_2000b):
            // B z_is_zero; RD sx; DD ex(sx); RD sy; DD ey(sy); [RD sz; DD ez(sz)]
            boolean zAreZero = r.getInput().readBit();
            double sx = r.readRawDouble();
            double ex = r.readDD(sx);
            double sy = r.readRawDouble();
            double ey = r.readDD(sy);
            double sz = 0, ez = 0;
            if (!zAreZero) {
                sz = r.readRawDouble();
                ez = r.readDD(sz);
            }
            line.setStart(new Point3D(sx, sy, sz));
            line.setEnd(new Point3D(ex, ey, ez));
        }
        line.setThickness(r.readBitThickness());
        line.setExtrusion(r.readBitExtrusion());
    }
}
