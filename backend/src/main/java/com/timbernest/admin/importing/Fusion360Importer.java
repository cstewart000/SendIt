package com.timbernest.admin.importing;

import com.timbernest.admin.Machine;
import com.timbernest.admin.Tool;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Parse Fusion 360 tool library JSON and best-effort machine JSON. */
@Component
public class Fusion360Importer {
    private static final Logger log = LoggerFactory.getLogger(Fusion360Importer.class);
    private final ObjectMapper mapper;

    public Fusion360Importer(ObjectMapper mapper) { this.mapper = mapper; }

    public List<Tool> parseTools(String json, Long machineId) throws Exception {
        JsonNode root = mapper.readTree(json);
        JsonNode data = root.path("data");
        if (!data.isArray()) data = root.path("tools");
        if (!data.isArray()) throw new IllegalArgumentException("Fusion tool library needs a data[] array");
        List<Tool> out = new ArrayList<>();
        for (JsonNode n : data) {
            Tool t = mapTool(n, machineId);
            if (t != null) out.add(t);
        }
        log.info("Fusion360 tools → {} for machine {}", out.size(), machineId);
        return out;
    }

    public Machine parseMachine(String json) throws Exception {
        JsonNode root = mapper.readTree(json);
        Machine m = new Machine();
        m.setPostProcessor("Fusion360");
        m.setName(text(root, "name", "description", "title", "machine"));
        if (m.getName() == null || m.getName().isBlank()) m.setName("Fusion Machine");
        JsonNode env = firstObj(root, "maximumWorkEnvelope", "workEnvelope", "geometry", "kinematics");
        m.setWorkXmm(num(env, 2440, "x", "X", "width", "maxX"));
        m.setWorkYmm(num(env, 1220, "y", "Y", "depth", "maxY"));
        m.setWorkZmm(num(env, 100, "z", "Z", "height", "maxZ"));
        m.setDefaultFeedMmMin(num(root, 3000, "maxFeedrate", "defaultFeed", "feedrate"));
        m.setDefaultSpeedRpm(num(root, 18000, "maxSpindleSpeed", "spindleSpeed", "rpm"));
        m.setHourlyRate(60);
        // Dimensions sometimes in cm/inch — if tiny, assume inches
        if (m.getWorkXmm() > 0 && m.getWorkXmm() < 50) {
            m.setWorkXmm(m.getWorkXmm() * 25.4);
            m.setWorkYmm(m.getWorkYmm() * 25.4);
            m.setWorkZmm(Math.max(10, m.getWorkZmm() * 25.4));
        }
        log.info("Fusion360 machine → {} {}x{}x{}", m.getName(), m.getWorkXmm(), m.getWorkYmm(), m.getWorkZmm());
        return m;
    }

    private Tool mapTool(JsonNode n, Long machineId) {
        JsonNode geo = n.path("geometry");
        if (geo.isMissingNode()) geo = n.path("Geometry");
        double dia = num(geo, num(n, 0, "diameter", "DC"), "DC", "diameter", "Diameter", "BODYDIAMETER");
        if (dia <= 0) return null;
        Tool t = new Tool();
        t.setMachineId(machineId);
        String desc = text(n, "description", "Description", "product-id", "type");
        t.setName(desc != null && !desc.isBlank() ? desc : "Fusion tool Ø" + dia);
        t.setType(mapType(text(n, "type", "Type", "BMC", "unit")));
        t.setDiameterMm(dia);
        t.setFluteCount((int) Math.max(1, num(geo, 2, "NOF", "fluteCount", "FLUTES")));
        t.setMaxDepthMm(Math.max(1, num(geo, 20, "LCF", "fluteLength", "LB", "shoulderLength", "OAL")));
        t.setWearCharge(2.5);
        return t;
    }

    private String mapType(String type) {
        String n = type == null ? "" : type.toLowerCase(Locale.ROOT);
        if (n.contains("ball")) return "BALLNOSE";
        if (n.contains("chamfer") || n.contains("v bit") || n.contains("engraving")) return "VBIT";
        if (n.contains("face") || n.contains("surfac")) return "SURFACING";
        if (n.contains("drill") || n.contains("spot")) return "DRILL";
        return "ENDMILL";
    }

    private JsonNode firstObj(JsonNode root, String... keys) {
        for (String k : keys) {
            JsonNode n = root.path(k);
            if (n.isObject()) return n;
        }
        return root;
    }

    private String text(JsonNode n, String... keys) {
        for (String k : keys) {
            JsonNode v = n.path(k);
            if (v.isValueNode() && !v.asString().isBlank()) return v.asString();
        }
        return null;
    }

    private double num(JsonNode n, double def, String... keys) {
        for (String k : keys) {
            JsonNode v = n.path(k);
            if (v.isNumber()) return v.asDouble();
            if (v.isString()) {
                try { return Double.parseDouble(v.asString().replaceAll("[^0-9eE.+-]", "")); }
                catch (Exception ignored) {}
            }
        }
        return def;
    }
}
