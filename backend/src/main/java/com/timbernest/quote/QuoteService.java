package com.timbernest.quote;

import com.timbernest.admin.*;
import com.timbernest.cam.PathMetrics;
import com.timbernest.geometry.model.GeometryModel;
import com.timbernest.nesting.NestResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class QuoteService {
    private static final Logger log = LoggerFactory.getLogger(QuoteService.class);

    public Map<String, Object> quote(NestResult nest, List<GeometryModel> geometries,
                                     Machine machine, Material material, Tool tool,
                                     PricingRuleRepository pricing) {
        Long mid = machine.getId();
        double setup = value(pricing, mid, "SETUP_FEE", 25);
        double minOrder = value(pricing, mid, "MIN_ORDER", 40);
        double markup = value(pricing, mid, "MATERIAL_MARKUP", 1.0);

        PathMetrics.Result metrics = PathMetrics.compute(nest, geometries, machine, tool, material);
        double cycleMin = metrics.cycleMinutes();
        double machineCost = (cycleMin / 60.0) * machine.getHourlyRate();
        double materialCost = nest.getSheetCount() * material.getCostPerSheet()
                * material.getScrapFactor() * markup;
        double toolCost = tool.getWearCharge() * Math.max(1, nest.getPlacements().size());
        double subtotal = machineCost + materialCost + toolCost + setup;
        double total = Math.max(subtotal, minOrder);

        List<Map<String, Object>> lines = List.of(
                line("material", "Material (sheets + scrap)", materialCost),
                line("machine", "Machine time", machineCost),
                line("tool", "Tool usage", toolCost),
                line("setup", "Setup / handling", setup)
        );
        Map<String, Object> quote = new LinkedHashMap<>();
        quote.put("currency", "AUD");
        quote.put("cycleMinutes", round(cycleMin));
        quote.put("pathLengthMm", round(metrics.cutLengthMm()));
        quote.put("plungeMm", round(metrics.plungeMm()));
        quote.put("passes", metrics.passes());
        quote.put("contourCount", metrics.contourCount());
        quote.put("sheetsUsed", nest.getSheetCount());
        quote.put("lines", lines);
        quote.put("total", round(total));
        quote.put("minimumApplied", total == minOrder && subtotal < minOrder);
        log.info("Quote total={} cycleMin={} cutMm={} passes={}", total, cycleMin,
                metrics.cutLengthMm(), metrics.passes());
        return quote;
    }

    private double value(PricingRuleRepository pricing, Long machineId, String key, double def) {
        return pricing.findByMachineIdAndRuleKey(machineId, key)
                .or(() -> pricing.findByRuleKey(key))
                .map(PricingRule::getValue)
                .orElse(def);
    }

    private Map<String, Object> line(String code, String label, double amount) {
        return Map.of("code", code, "label", label, "amount", round(amount));
    }

    private double round(double v) { return Math.round(v * 100.0) / 100.0; }
}
