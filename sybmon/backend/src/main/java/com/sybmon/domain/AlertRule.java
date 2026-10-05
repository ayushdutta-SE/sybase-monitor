package com.sybmon.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "alert_rule")
@Getter @Setter
public class AlertRule {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "metric_name") private String metricName;
    private String comparator;
    private double threshold;
    private String severity;
    private boolean enabled = true;

    public boolean breached(double v) {
        return ">".equals(comparator) ? v > threshold : v < threshold;
    }
}
