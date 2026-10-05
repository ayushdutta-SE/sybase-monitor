package com.sybmon.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

@Entity
@Table(name = "sybase_server")
@Getter @Setter
public class SybaseServer {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    private String host;
    private int port;
    private String username;
    @Column(name = "password_enc")
    private String passwordEnc;
    private boolean enabled = true;
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;
}
