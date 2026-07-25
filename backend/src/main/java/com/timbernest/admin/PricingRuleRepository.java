package com.timbernest.admin;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface PricingRuleRepository extends JpaRepository<PricingRule, Long> {
    Optional<PricingRule> findByRuleKey(String ruleKey);
}
