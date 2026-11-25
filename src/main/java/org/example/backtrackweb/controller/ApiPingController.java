package org.example.backtrackweb.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Health probe for the machine API.
 *
 * <p>This is the only public endpoint of {@code /api/v1} in Phase 1. It exists
 * so the desktop and Android shells have something to check before loading the
 * application, and so the separate token-based security chain can be verified
 * without exposing any business data. Sync endpoints arrive in Phase 2.
 */
@RestController
@RequestMapping("/api/v1")
public class ApiPingController {

    @GetMapping("/ping")
    public Map<String, Object> ping() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "ok");
        body.put("app", "BackTrack");
        body.put("apiVersion", "v1");
        body.put("serverTime", Instant.now().toString());
        return body;
    }
}
