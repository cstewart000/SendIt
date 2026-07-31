package com.timbernest.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Fails fast in production-like environments when insecure defaults remain.
 */
@Component
public class ProdSafetyConfig implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(ProdSafetyConfig.class);
    private static final String DEV_JWT = "sendit-dev-secret-change-me-32chars!!!!";

    private final String jwtSecret;
    private final String datasourceUrl;

    public ProdSafetyConfig(
            @Value("${sendit.jwt-secret}") String jwtSecret,
            @Value("${spring.datasource.url:}") String datasourceUrl) {
        this.jwtSecret = jwtSecret;
        this.datasourceUrl = datasourceUrl == null ? "" : datasourceUrl;
    }

    @Override
    public void run(ApplicationArguments args) {
        boolean railway = System.getenv("RAILWAY_ENVIRONMENT") != null
                || System.getenv("RAILWAY_PROJECT_ID") != null;
        boolean remoteDb = datasourceUrl.contains("railway")
                || datasourceUrl.contains("rlwy.net")
                || datasourceUrl.contains("amazonaws.com");
        boolean prodProfile = ArraysContainsProfile("prod", "production");

        if ((railway || remoteDb || prodProfile) && DEV_JWT.equals(jwtSecret)) {
            throw new IllegalStateException(
                    "JWT_SECRET is still the dev default — set JWT_SECRET before deploying");
        }
        if (DEV_JWT.equals(jwtSecret)) {
            log.warn("Using dev JWT secret — fine for local only");
        }
    }

    private static boolean ArraysContainsProfile(String... names) {
        String active = System.getenv("SPRING_PROFILES_ACTIVE");
        if (active == null) active = System.getProperty("spring.profiles.active", "");
        String lower = active.toLowerCase();
        for (String n : names) {
            if (lower.contains(n)) return true;
        }
        return false;
    }
}
