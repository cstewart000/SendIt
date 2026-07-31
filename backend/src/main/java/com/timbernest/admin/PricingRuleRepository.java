package com.timbernest.admin;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PricingRuleRepository extends JpaRepository<PricingRule, Long> {
    List<PricingRule> findByMachineId(Long machineId);

    Optional<PricingRule> findByMachineIdAndRuleKey(Long machineId, String ruleKey);

    Optional<PricingRule> findByRuleKey(String ruleKey);
}
