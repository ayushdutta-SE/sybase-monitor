package com.sybmon.collector;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.*;

/** All SQL that runs against Sybase ASE lives here. Read-only queries only. */
@Slf4j
@Component
public class SybaseCollector {

    public record Sample(String name, String label, double value) {}

    @FunctionalInterface
    private interface Step { void run() throws SQLException; }

    public static final String PROCESSES = """
            select top 50 spid, suser_name(suid) as login, db_name(dbid) as db, hostname, program_name,
                   status, cmd, blocked, cpu, physical_io, memusage
              from master..sysprocesses
             where suid > 0
             order by cpu desc
            """;

    public static final String BLOCKING = """
            select spid, suser_name(suid) as login, db_name(dbid) as db, hostname, program_name,
                   status, cmd, blocked, cpu, physical_io
              from master..sysprocesses
             where blocked > 0
                or spid in (select blocked from master..sysprocesses where blocked > 0)
             order by blocked, spid
            """;

    /** One polling pass. Each probe is isolated so a missing permission never hides the rest. */
    public List<Sample> collect(Connection c) {
        List<Sample> out = new ArrayList<>();
        out.add(new Sample("availability", "", 1));
        safe("connections.total", () -> out.add(new Sample("connections.total", "",
                scalar(c, "select count(*) from master..sysprocesses where suid > 0"))));
        safe("connections.blocked", () -> out.add(new Sample("connections.blocked", "",
                scalar(c, "select count(*) from master..sysprocesses where blocked > 0"))));
        safe("tx.long_running", () -> out.add(new Sample("tx.long_running", "",
                scalar(c, "select count(*) from master..systransactions where starttime < dateadd(mi, -5, getdate())"))));
        safe("databases", () -> databases(c, out));
        safe("cache (needs MDA tables + mon_role)", () -> cache(c, out));
        return out;
    }

    private void databases(Connection c, List<Sample> out) throws SQLException {
        String sql = """
                select d.name as db,
                       sum(u.size) * (@@maxpagesize / 1048576.0) as size_mb,
                       sum(case when u.segmap & 4 = 4 then u.size else 0 end) as log_pages,
                       lct_admin('logsegment_freepages', d.dbid) as free_pages
                  from master..sysdatabases d, master..sysusages u
                 where d.dbid = u.dbid and d.status & 288 = 0 and d.status2 & 48 = 0
                 group by d.name, d.dbid
                """;
        try (var st = c.createStatement()) {
            st.setQueryTimeout(15);
            try (ResultSet rs = st.executeQuery(sql)) {
                while (rs.next()) {
                    String db = rs.getString("db");
                    out.add(new Sample("db.size_mb", db, rs.getDouble("size_mb")));
                    double logPages = rs.getDouble("log_pages");
                    if (logPages > 0) {
                        double used = 100.0 * (logPages - rs.getDouble("free_pages")) / logPages;
                        out.add(new Sample("db.log_used_pct", db, Math.max(0, Math.min(100, used))));
                    }
                }
            }
        }
    }

    /** NOTE: counters are cumulative since server start. Good enough for a baseline; use deltas for tuning work. */
    private void cache(Connection c, List<Sample> out) throws SQLException {
        try (var st = c.createStatement()) {
            st.setQueryTimeout(10);
            try (ResultSet rs = st.executeQuery("select CacheName, CacheSearches, PhysicalReads from master..monDataCache")) {
                while (rs.next()) {
                    double searches = rs.getDouble("CacheSearches");
                    if (searches > 0) {
                        double hit = 100.0 * (1 - rs.getDouble("PhysicalReads") / searches);
                        out.add(new Sample("cache.hit_ratio", rs.getString("CacheName"), Math.max(0, Math.min(100, hit))));
                    }
                }
            }
        }
    }

    public List<Map<String, Object>> rows(Connection c, String sql) throws SQLException {
        List<Map<String, Object>> list = new ArrayList<>();
        try (var st = c.createStatement()) {
            st.setQueryTimeout(15);
            try (ResultSet rs = st.executeQuery(sql)) {
                ResultSetMetaData md = rs.getMetaData();
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= md.getColumnCount(); i++) {
                        Object v = rs.getObject(i);
                        row.put(md.getColumnLabel(i), v instanceof String s ? s.trim() : v);
                    }
                    list.add(row);
                }
            }
        }
        return list;
    }

    private double scalar(Connection c, String sql) throws SQLException {
        try (var st = c.createStatement()) {
            st.setQueryTimeout(10);
            try (ResultSet rs = st.executeQuery(sql)) {
                rs.next();
                return rs.getDouble(1);
            }
        }
    }

    private void safe(String what, Step step) {
        try { step.run(); } catch (SQLException | RuntimeException e) {
            log.warn("probe '{}' failed: {}", what, e.getMessage());
        }
    }
}
