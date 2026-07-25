package com.timbernest.geometry;

import com.timbernest.common.ApiException;
import com.timbernest.geometry.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class GeometryRepairer {
    private static final Logger log = LoggerFactory.getLogger(GeometryRepairer.class);
    private static final double CLOSE_EPS = 1.0;

    public String apply(GeometryModel model, String action) {
        return switch (action.toUpperCase()) {
            case "CLOSE_OPEN_CONTOURS" -> closeOpen(model);
            case "REMOVE_ZERO_LENGTH" -> removeZero(model);
            case "REMOVE_DUPLICATES" -> removeDupes(model);
            case "PURGE_NON_GEOMETRY" -> purgeLayers(model);
            case "COLLAPSE_TINY" -> collapseTiny(model);
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "Unknown repair action: " + action);
        };
    }

    private String closeOpen(GeometryModel model) {
        int n = 0;
        for (Contour c : model.getContours()) {
            if (!c.isClosed() && c.getPoints().size() >= 2) {
                Vec2 a = c.getPoints().get(0);
                Vec2 b = c.getPoints().get(c.getPoints().size() - 1);
                if (a.dist(b) <= CLOSE_EPS) {
                    c.getPoints().set(c.getPoints().size() - 1, a);
                    c.setClosed(true);
                    n++;
                } else if (a.dist(b) <= 5) {
                    c.getPoints().add(a);
                    c.setClosed(true);
                    n++;
                }
            }
        }
        log.info("Closed {} contours", n);
        return "Closed " + n + " near-open contours";
    }

    private String removeZero(GeometryModel model) {
        int before = model.getContours().size();
        model.setContours(model.getContours().stream()
                .filter(c -> c.getPoints().size() >= 2 && c.pathLength() >= 0.05)
                .collect(Collectors.toCollection(ArrayList::new)));
        int removed = before - model.getContours().size();
        log.info("Removed {} zero-length entities", removed);
        return "Removed " + removed + " zero-length entities";
    }

    private String removeDupes(GeometryModel model) {
        List<Contour> unique = new ArrayList<>();
        int removed = 0;
        for (Contour c : model.getContours()) {
            boolean dupe = unique.stream().anyMatch(u -> same(u, c));
            if (dupe) removed++; else unique.add(c);
        }
        model.setContours(unique);
        log.info("Removed {} duplicates", removed);
        return "Removed " + removed + " duplicates";
    }

    private String purgeLayers(GeometryModel model) {
        int before = model.getContours().size();
        List<Contour> kept = new ArrayList<>();
        for (Contour c : model.getContours()) {
            String l = c.getLayer() == null ? "" : c.getLayer().toLowerCase();
            if (l.contains("text") || l.contains("dim") || l.contains("defpoints")) {
                model.getPurgedLayers().add(c.getLayer());
            } else kept.add(c);
        }
        model.setContours(kept);
        return "Purged " + (before - kept.size()) + " non-geometry entities";
    }

    private String collapseTiny(GeometryModel model) {
        int collapsed = 0;
        for (Contour c : model.getContours()) {
            List<Vec2> pts = new ArrayList<>();
            for (Vec2 p : c.getPoints()) {
                if (pts.isEmpty() || pts.get(pts.size() - 1).dist(p) >= 0.2) pts.add(p);
                else collapsed++;
            }
            c.setPoints(pts);
        }
        return "Collapsed " + collapsed + " tiny segments";
    }

    private boolean same(Contour a, Contour b) {
        if (a.getPoints().size() != b.getPoints().size()) return false;
        for (int i = 0; i < a.getPoints().size(); i++) {
            if (a.getPoints().get(i).dist(b.getPoints().get(i)) > 0.05) return false;
        }
        return true;
    }
}
