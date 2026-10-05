package com.sybmon.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ServerRepo extends JpaRepository<SybaseServer, Long> {
    List<SybaseServer> findByEnabledTrue();
}
