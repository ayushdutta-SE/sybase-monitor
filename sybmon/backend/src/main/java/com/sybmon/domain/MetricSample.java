package com.sybmon.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.Instant;

@Entity
@Table(name = "metric_sample")
@Getter @NoArgsConstructor
public class MetricSample {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "server_id") private Long serverId;
    private String name;
    private String label;
    @Column(name = "metric_value") private double value;
    private Instant ts;

    public MetricSample(Long serverId, String name, String label, double value, Instant ts) {
        this.serverId = serverId; this.name = name; this.label = label; this.value = value; this.ts = ts;
    }
}
