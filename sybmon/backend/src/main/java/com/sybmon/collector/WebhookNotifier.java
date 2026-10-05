package com.sybmon.collector;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/** Slack/Teams-compatible incoming webhook ({"text": "..."}). No-op when URL is blank. */
@Slf4j
@Component
public class WebhookNotifier {
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper mapper;
    private final String url;

    public WebhookNotifier(ObjectMapper mapper, @Value("${monitor.webhook-url:}") String url) {
        this.mapper = mapper;
        this.url = url;
    }

    public void send(String text) {
        if (url.isBlank()) return;
        try {
            var req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(Map.of("text", text)))).build();
            http.sendAsync(req, HttpResponse.BodyHandlers.discarding())
                .exceptionally(e -> { log.warn("webhook failed: {}", e.getMessage()); return null; });
        } catch (JsonProcessingException | RuntimeException e) {
            log.warn("webhook failed: {}", e.getMessage());
        }
    }
}
