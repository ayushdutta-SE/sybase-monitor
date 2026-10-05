package com.sybmon.collector;

import com.sybmon.collector.SybaseCollector.Sample;
import com.sybmon.domain.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.Executors;

@Slf4j
@Component
@RequiredArgsConstructor
public class MetricPoller {
    private final ServerRepo servers;
    private final MetricRepo metrics;
    private final SybaseClient client;
    private final SybaseCollector collector;
    private final AlertService alerts;

    @Value("${monitor.retention-days}")
    private int retentionDays;

    /** fixedDelay: a slow pass can never overlap the next one. One virtual thread per server. */
    @Scheduled(fixedDelayString = "${monitor.poll-interval-ms}", initialDelay = 5000)
    public void poll() {
        List<SybaseServer> targets = servers.findByEnabledTrue();
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            targets.forEach(s -> pool.submit(() -> pollOne(s)));
        }
    }

    private void pollOne(SybaseServer s) {
        List<Sample> samples;
        try (Connection c = client.open(s)) {
            samples = collector.collect(c);
        } catch (Exception e) {
            log.warn("server '{}' unreachable: {}", s.getName(), e.getMessage());
            samples = List.of(new Sample("availability", "", 0));
        }
        try {
            Instant now = Instant.now();
            metrics.saveAll(samples.stream().map(m -> new MetricSample(s.getId(), m.name(), m.label(), m.value(), now)).toList());
            alerts.evaluate(s, samples);
        } catch (Exception e) {
            log.error("persisting metrics for '{}' failed", s.getName(), e);
        }
    }

    @Scheduled(cron = "0 15 * * * *")
    public void purge() {
        int n = metrics.purge(Instant.now().minus(retentionDays, ChronoUnit.DAYS));
        if (n > 0) log.info("purged {} old samples", n);
    }
}
