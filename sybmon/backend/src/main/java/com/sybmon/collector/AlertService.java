package com.sybmon.collector;

import com.sybmon.collector.SybaseCollector.Sample;
import com.sybmon.domain.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/** Threshold evaluation with open/resolve lifecycle and de-duplication per (server, rule, label). */
@Service
@RequiredArgsConstructor
public class AlertService {
    private static final List<String> ACTIVE = List.of(Alert.OPEN, Alert.ACK);

    private final RuleRepo rules;
    private final AlertRepo alerts;
    private final WebhookNotifier notifier;

    @Transactional
    public void evaluate(SybaseServer server, List<Sample> samples) {
        for (AlertRule rule : rules.findByEnabledTrue()) {
            for (Sample m : samples) {
                if (!m.name().equals(rule.getMetricName())) continue;
                boolean breach = rule.breached(m.value());
                var active = alerts.findFirstByServerIdAndRuleIdAndLabelAndStatusIn(server.getId(), rule.getId(), m.label(), ACTIVE);
                if (breach && active.isEmpty()) {
                    Alert a = new Alert();
                    a.setServerId(server.getId());
                    a.setRuleId(rule.getId());
                    a.setLabel(m.label());
                    a.setStatus(Alert.OPEN);
                    a.setSeverity(rule.getSeverity());
                    a.setMetricValue(m.value());
                    a.setOpenedAt(Instant.now());
                    a.setMessage("[%s] %s: %s%s %s %s (now %.2f)".formatted(rule.getSeverity(), server.getName(), m.name(),
                            m.label().isEmpty() ? "" : "[" + m.label() + "]", rule.getComparator(), rule.getThreshold(), m.value()));
                    alerts.save(a);
                    notifier.send(a.getMessage());
                } else if (!breach && active.isPresent()) {
                    Alert a = active.get();
                    a.setStatus(Alert.RESOLVED);
                    a.setResolvedAt(Instant.now());
                    notifier.send("[RESOLVED] " + a.getMessage());
                }
            }
        }
    }
}
