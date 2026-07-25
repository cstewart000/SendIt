package com.timbernest.cam;

import com.timbernest.admin.Machine;
import com.timbernest.admin.Material;
import com.timbernest.admin.Tool;
import com.timbernest.nesting.NestResult;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;

@Component
public class SetupSheetWriter {
    public String write(Long jobId, Machine machine, Tool tool, Material material,
                        NestResult nest, Map<String, Object> quote) {
        return """
                SendIt Setup Sheet
                ======================
                Job ID: %d
                Machine: %s (%s)
                Material: %s (%.0f mm)
                Tool: %s Ø%.2f mm, %d flutes
                Sheets: %d  (%.0f x %.0f mm)
                Feed: %.0f mm/min   Speed: %.0f RPM
                Estimated cycle: %s minutes
                Quote total: %s %s

                Notes:
                - Verify origin at front-left of sheet
                - Secure sheet with dogs/clamps clear of toolpaths
                - Dry-run recommended before cutting
                """.formatted(
                jobId,
                machine.getName(), machine.getPostProcessor(),
                material.getName(), material.getThicknessMm(),
                tool.getName(), tool.getDiameterMm(), tool.getFluteCount(),
                nest.getSheetCount(), nest.getSheetWidth(), nest.getSheetHeight(),
                machine.getDefaultFeedMmMin(), machine.getDefaultSpeedRpm(),
                String.format(Locale.US, "%.2f", quote.get("cycleMinutes")),
                quote.get("currency"), quote.get("total")
        );
    }
}
