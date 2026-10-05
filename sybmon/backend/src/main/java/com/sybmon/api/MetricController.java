package com.sybmon.api;

import com.sybmon.domain.MetricRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@RestController
@RequestMapping("/api/servers/{id}")
@RequiredArgsConstructor
public class MetricController {

    public record Point(Instant ts, double value) {}
    public record Latest(String name, String label, double value, Instant ts) {}

    private final MetricRepo repo;

    /** Time series for one metric. Window capped at 7 days (~20k points at the default 30s interval). */
    @GetMapping("/metrics")
    public List<Point> series(@PathVariable Long id, @RequestParam String name,
                              @RequestParam(defaultValue = "") String label,
                              @RequestParam(defaultValue = "60") int minutes) {
        Instant to = Instant.now();
        Instant from = to.minus(Math.max(1, Math.min(minutes, 7 * 24 * 60)), ChronoUnit.MINUTES);
        return repo.findByServerIdAndNameAndLabelAndTsBetweenOrderByTsAsc(id, name, label, from, to)
                .stream().map(m -> new Point(m.getTs(), m.getValue())).toList();
    }

    /** Latest value of every metric seen in the last 10 minutes. */
    @GetMapping("/summary")
    public List<Latest> summary(@PathVariable Long id) {
        return repo.latest(id, Instant.now().minus(10, ChronoUnit.MINUTES)).stream()
                .map(m -> new Latest(m.getName(), m.getLabel(), m.getValue(), m.getTs())).toList();
    }
}
