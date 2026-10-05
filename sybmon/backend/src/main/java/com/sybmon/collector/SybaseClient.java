package com.sybmon.collector;

import com.sybmon.config.CryptoService;
import com.sybmon.domain.SybaseServer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/** Opens short-lived, timeout-bounded connections to a monitored ASE instance. */
@Component
@RequiredArgsConstructor
public class SybaseClient {
    private final CryptoService crypto;

    public Connection open(SybaseServer s) throws SQLException {
        Properties p = new Properties();
        p.setProperty("user", s.getUsername());
        p.setProperty("password", crypto.decrypt(s.getPasswordEnc()));
        p.setProperty("loginTimeout", "5");   // seconds
        p.setProperty("socketTimeout", "20"); // seconds
        p.setProperty("appName", "sybmon");
        String url = "jdbc:jtds:sybase://%s:%d/master".formatted(s.getHost(), s.getPort());
        return DriverManager.getConnection(url, p);
    }
}
