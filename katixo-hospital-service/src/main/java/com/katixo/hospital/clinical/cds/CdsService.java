package com.katixo.hospital.clinical.cds;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/** Runs all registered {@link CdsRule}s and aggregates their alerts. Fail-open per rule. */
@Service
@RequiredArgsConstructor
@Slf4j
public class CdsService {

    private final List<CdsRule> rules;

    public List<CdsAlert> evaluate(CdsRule.Context context) {
        List<CdsAlert> alerts = new ArrayList<>();
        for (CdsRule rule : rules) {
            try {
                List<CdsAlert> ruleAlerts = rule.evaluate(context);
                if (ruleAlerts != null) {
                    alerts.addAll(ruleAlerts);
                }
            } catch (Exception e) {
                // A broken rule must never block clinical care.
                log.warn("CDS rule {} failed: {}", rule.getClass().getSimpleName(), e.getMessage());
            }
        }
        return alerts;
    }

    public boolean hasBlocking(List<CdsAlert> alerts) {
        return alerts.stream().anyMatch(a -> a.severity() == CdsAlert.Severity.CRITICAL);
    }
}
