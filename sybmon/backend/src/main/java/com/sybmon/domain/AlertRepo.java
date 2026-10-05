package com.sybmon.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AlertRepo extends JpaRepository<Alert, Long> {
    Optional<Alert> findFirstByServerIdAndRuleIdAndLabelAndStatusIn(Long serverId, Long ruleId, String label, Collection<String> status);
    List<Alert> findTop200ByStatusInOrderByOpenedAtDesc(Collection<String> status);
    List<Alert> findTop200ByOrderByOpenedAtDesc();
}
