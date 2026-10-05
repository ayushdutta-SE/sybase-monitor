package com.sybmon.api;

import com.sybmon.collector.SybaseClient;
import com.sybmon.collector.SybaseCollector;
import com.sybmon.config.CryptoService;
import com.sybmon.domain.ServerRepo;
import com.sybmon.domain.SybaseServer;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Connection;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/servers")
@RequiredArgsConstructor
public class ServerController {

    public record ServerRequest(@NotBlank String name, @NotBlank String host,
                                @Min(1) @Max(65535) int port, @NotBlank String username,
                                String password, Boolean enabled) {}

    public record ServerView(Long id, String name, String host, int port, String username, boolean enabled) {
        static ServerView of(SybaseServer s) {
            return new ServerView(s.getId(), s.getName(), s.getHost(), s.getPort(), s.getUsername(), s.isEnabled());
        }
    }

    private final ServerRepo repo;
    private final CryptoService crypto;
    private final SybaseClient client;
    private final SybaseCollector collector;

    @GetMapping
    public List<ServerView> list() {
        return repo.findAll().stream().map(ServerView::of).toList();
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ServerView create(@Valid @RequestBody ServerRequest r) {
        if (r.password() == null || r.password().isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "password is required");
        return ServerView.of(repo.save(apply(new SybaseServer(), r)));
    }

    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ServerView update(@PathVariable Long id, @Valid @RequestBody ServerRequest r) {
        return ServerView.of(repo.save(apply(get(id), r)));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        repo.delete(get(id));
    }

    @PostMapping("/{id}/test")
    public Map<String, Object> test(@PathVariable Long id) {
        try (Connection c = client.open(get(id))) {
            var v = collector.rows(c, "select @@version as version");
            return Map.of("ok", true, "detail", String.valueOf(v.get(0).get("version")));
        } catch (Exception e) {
            return Map.of("ok", false, "detail", String.valueOf(e.getMessage()));
        }
    }

    @GetMapping("/{id}/live/processes")
    public List<Map<String, Object>> processes(@PathVariable Long id) throws Exception {
        try (Connection c = client.open(get(id))) { return collector.rows(c, SybaseCollector.PROCESSES); }
    }

    @GetMapping("/{id}/live/blocking")
    public List<Map<String, Object>> blocking(@PathVariable Long id) throws Exception {
        try (Connection c = client.open(get(id))) { return collector.rows(c, SybaseCollector.BLOCKING); }
    }

    private SybaseServer get(Long id) {
        return repo.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private SybaseServer apply(SybaseServer s, ServerRequest r) {
        s.setName(r.name());
        s.setHost(r.host());
        s.setPort(r.port());
        s.setUsername(r.username());
        if (r.password() != null && !r.password().isBlank()) s.setPasswordEnc(crypto.encrypt(r.password()));
        if (r.enabled() != null) s.setEnabled(r.enabled());
        return s;
    }
}
