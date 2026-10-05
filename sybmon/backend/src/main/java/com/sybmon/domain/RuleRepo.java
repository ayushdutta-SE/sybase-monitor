package com.sybmon.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface RuleRepo extends JpaRepository<AlertRule, Long> {
    List<AlertRule> findByEnabledTrue();
}
