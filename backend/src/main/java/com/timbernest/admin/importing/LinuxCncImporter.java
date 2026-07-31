package com.timbernest.admin.importing;

import com.timbernest.admin.Machine;
import com.timbernest.admin.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parse LinuxCNC .ini machine configs and tool.tbl libraries. */
@Component
public class LinuxCncImporter {
    private static final Logger log = LoggerFactory.getLogger(LinuxCncImporter.class);
    private static final Pattern SECTION = Pattern.compile("^\\[([^\\]]+)\\]\\s*$");
    private static final Pattern KV = Pattern.compile("^([A-Za-z0-9_]+)\\s*=\\s*(.+)$");

    public Machine parseIni(String text) {
        Map<String, Map<String, String>> ini = parseSections(text);
        Machine m = new Machine();
        m.setPostProcessor("LinuxCNC");
        String name = first(ini, "EMC", "MACHINE", "DISPLAY", "MACHINE", "TITLE");
        m.setName(name != null && !name.isBlank() ? name.trim() : "LinuxCNC Machine");
        double units = "inch".equalsIgnoreCase(val(ini, "TRAJ", "LINEAR_UNITS")) ? 25.4 : 1.0;
        m.setWorkXmm(axisTravel(ini, "AXIS_X", "X") * units);
        m.setWorkYmm(axisTravel(ini, "AXIS_Y", "Y") * units);
        m.setWorkZmm(Math.max(10, axisTravel(ini, "AXIS_Z", "Z") * units));
        double vel = num(val(ini, "TRAJ", "MAX_LINEAR_VELOCITY"), 50);
        m.setDefaultFeedMmMin(Math.round(vel * units * 60));
        double rpm = num(first(ini, "SPINDLE_0", "MAX_FORWARD_VELOCITY",
                "SPINDLE", "MAX_FORWARD_VELOCITY", "SPINDLE_9", "MAX_FORWARD_VELOCITY"), 18000);
        m.setDefaultSpeedRpm(rpm > 1000 ? rpm : rpm * 60);
        m.setHourlyRate(60);
        log.info("LinuxCNC INI → machine={} work={}x{}x{} feed={}",
                m.getName(), m.getWorkXmm(), m.getWorkYmm(), m.getWorkZmm(), m.getDefaultFeedMmMin());
        return m;
    }

    public List<Tool> parseToolTable(String text, Long machineId) {
        List<Tool> out = new ArrayList<>();
        for (String raw : text.split("\\R")) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith(";")) continue;
            Matcher t = Pattern.compile("(?i)\\bT(\\d+)\\b").matcher(line);
            if (!t.find()) continue;
            double dia = matchNum(line, "(?i)\\bD([-+]?\\d*\\.?\\d+)");
            double z = Math.abs(matchNum(line, "(?i)\\bZ([-+]?\\d*\\.?\\d+)"));
            String comment = "";
            int sc = line.indexOf(';');
            if (sc >= 0) comment = line.substring(sc + 1).trim();
            Tool tool = new Tool();
            tool.setMachineId(machineId);
            tool.setName(comment.isBlank() ? "T" + t.group(1) : comment);
            tool.setType(guessType(comment));
            tool.setDiameterMm(dia > 0 ? dia : 6);
            tool.setMaxDepthMm(z > 0 ? z : 20);
            tool.setFluteCount(2);
            tool.setWearCharge(2.5);
            out.add(tool);
        }
        log.info("LinuxCNC tool.tbl → {} tools for machine {}", out.size(), machineId);
        return out;
    }

    private Map<String, Map<String, String>> parseSections(String text) {
        Map<String, Map<String, String>> map = new LinkedHashMap<>();
        String section = "";
        for (String raw : text.split("\\R")) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#") || line.startsWith(";")) continue;
            Matcher sm = SECTION.matcher(line);
            if (sm.matches()) { section = sm.group(1).toUpperCase(Locale.ROOT); map.putIfAbsent(section, new LinkedHashMap<>()); continue; }
            Matcher km = KV.matcher(line);
            if (km.matches() && !section.isEmpty()) {
                map.get(section).put(km.group(1).toUpperCase(Locale.ROOT), km.group(2).trim());
            }
        }
        return map;
    }

    private double axisTravel(Map<String, Map<String, String>> ini, String axis, String shortName) {
        String min = val(ini, axis, "MIN_LIMIT");
        String max = val(ini, axis, "MAX_LIMIT");
        double a = num(min, 0);
        double b = num(max, "AXIS_X".equals(axis) ? 2440 : "AXIS_Y".equals(axis) ? 1220 : 100);
        return Math.max(1, Math.abs(b - a));
    }

    private String first(Map<String, Map<String, String>> ini, String... secKey) {
        for (int i = 0; i + 1 < secKey.length; i += 2) {
            String v = val(ini, secKey[i], secKey[i + 1]);
            if (v != null) return v;
        }
        return null;
    }

    private String val(Map<String, Map<String, String>> ini, String sec, String key) {
        Map<String, String> s = ini.get(sec.toUpperCase(Locale.ROOT));
        return s == null ? null : s.get(key.toUpperCase(Locale.ROOT));
    }

    private double num(String s, double def) {
        if (s == null || s.isBlank()) return def;
        try { return Double.parseDouble(s.trim().split("\\s+")[0]); }
        catch (Exception e) { return def; }
    }

    private double matchNum(String line, String regex) {
        Matcher m = Pattern.compile(regex).matcher(line);
        if (!m.find()) return 0;
        try { return Double.parseDouble(m.group(1)); } catch (Exception e) { return 0; }
    }

    private String guessType(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        if (n.contains("ball")) return "BALLNOSE";
        if (n.contains("v-bit") || n.contains("vbit") || n.contains("engraving")) return "VBIT";
        if (n.contains("face") || n.contains("surfac")) return "SURFACING";
        if (n.contains("drill")) return "DRILL";
        return "ENDMILL";
    }
}
