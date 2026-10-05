package com.sybmon.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.List;

public interface MetricRepo extends JpaRepository<MetricSample, Long> {

    List<MetricSample> findByServerIdAndNameAndLabelAndTsBetweenOrderByTsAsc(
            Long serverId, String name, String label, Instant from, Instant to);

    @Query("""
           select m from MetricSample m
            where m.serverId = :sid
              and m.ts = (select max(x.ts) from MetricSample x
                           where x.serverId = :sid and x.name = m.name and x.label = m.label
                             and x.ts > :since)
           """)
    List<MetricSample> latest(@Param("sid") Long serverId, @Param("since") Instant since);

    @Modifying
    @Transactional
    @Query("delete from MetricSample m where m.ts < :cutoff")
    int purge(@Param("cutoff") Instant cutoff);
}
