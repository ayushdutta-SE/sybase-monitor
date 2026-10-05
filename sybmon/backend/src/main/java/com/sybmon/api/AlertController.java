package com.sybmon.api;

import com.sybmon.domain.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AlertController {

    public record RuleRequest(@NotBlank String metricName,
                              @Pattern(regexp = "[<>]") String comparator,
                              double threshold,
                              @Pattern(regexp = "INFO|WARNING|CRITICAL") String severity) {}

    private final AlertRepo alerts;
    private final RuleRepo rules;

    /** status = comma-separated subset of OPEN,ACK,RESOLVED; omit for everything (latest 200). */
    @GetMapping("/alerts")
    public List<Alert> list(@RequestParam(required = false) String status) {
        if (status == null || status.isBlank()) return alerts.findTop200ByOrderByOpenedAtDesc();
        return alerts.findTop200ByStatusInOrderByOpenedAtDesc(Arrays.stream(status.split(",")).map(String::trim).toList());
    }

    @PostMapping("/alerts/{id}/ack")
    public Alert ack(@PathVariable Long id) {
        Alert a = alerts.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (Alert.OPEN.equals(a.getStatus())) { a.setStatus(Alert.ACK); alerts.save(a); }
        return a;
    }

    @GetMapping("/rules")
    public List<AlertRule> rules() { return rules.findAll(); }

    @PostMapping(value = "/rules", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public AlertRule addRule(@Valid @RequestBody RuleRequest r) {
        AlertRule rule = new AlertRule();
        rule.setMetricName(r.metricName());
        rule.setComparator(r.comparator());
        rule.setThreshold(r.threshold());
        rule.setSeverity(r.severity());
        return rules.save(rule);
    }

    @DeleteMapping("/rules/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRule(@PathVariable Long id) { rules.deleteById(id); }
}
