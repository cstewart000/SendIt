package com.timbernest.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

/**
 * Production Postgres often lags Hibernate ddl-auto. Add new columns explicitly
 * so deploys don't crash on SeedData / JPA queries.
 */
@Component
@Order(1)
public class SchemaPatcher implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(SchemaPatcher.class);
    private final DataSource dataSource;

    public SchemaPatcher(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) {
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            // Machine CAM settings
            exec(s, "ALTER TABLE machine ADD COLUMN IF NOT EXISTS kerf_mm double precision DEFAULT 8");
            exec(s, "ALTER TABLE machine ADD COLUMN IF NOT EXISTS fixing_min_tool_distance_mm double precision DEFAULT 10");
            exec(s, "ALTER TABLE machine ADD COLUMN IF NOT EXISTS fixing_hole_diameter_mm double precision DEFAULT 4");
            exec(s, "ALTER TABLE machine ADD COLUMN IF NOT EXISTS fixings_enabled boolean DEFAULT true");
            exec(s, "ALTER TABLE machine ADD COLUMN IF NOT EXISTS tabs_enabled boolean DEFAULT true");
            exec(s, "ALTER TABLE machine ADD COLUMN IF NOT EXISTS tab_width_mm double precision DEFAULT 5");
            exec(s, "ALTER TABLE machine ADD COLUMN IF NOT EXISTS tab_height_mm double precision DEFAULT 1.5");
            exec(s, "ALTER TABLE machine ADD COLUMN IF NOT EXISTS tab_count integer DEFAULT 4");
            // Job CAM options JSON
            exec(s, "ALTER TABLE jobs ADD COLUMN IF NOT EXISTS cam_json text DEFAULT '{}'");
            // Dog-bone undo snapshot
            exec(s, "ALTER TABLE design_version ADD COLUMN IF NOT EXISTS pre_dogbone_geometry_json text");
            // Pricing rule value was already present; no-op safe
            log.info("SchemaPatcher applied machine/job CAM + dogbone columns");
        } catch (Exception e) {
            log.error("SchemaPatcher failed: {}", e.toString());
            throw new IllegalStateException("Schema patch failed: " + e.getMessage(), e);
        }
    }

    private void exec(Statement s, String sql) {
        try {
            s.execute(sql);
        } catch (Exception e) {
            log.warn("Schema patch skip/fail: {} — {}", sql, e.getMessage());
        }
    }
}
