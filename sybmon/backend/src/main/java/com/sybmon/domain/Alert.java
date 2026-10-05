package com.sybmon.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

@Entity
@Table(name = "alert")
@Getter @Setter
public class Alert {
    public static final String OPEN = "OPEN", ACK = "ACK", RESOLVED = "RESOLVED";

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "server_id") private Long serverId;
    @Column(name = "rule_id") private Long ruleId;
    private String label;
    private String status;
    private String severity;
    private String message;
    @Column(name = "metric_value") private double metricValue;
    @Column(name = "opened_at") private Instant openedAt;
    @Column(name = "resolved_at") private Instant resolvedAt;
}
