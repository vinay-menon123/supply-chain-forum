package com.cscen.forum.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class HealthController {

    private static final Logger log = LoggerFactory.getLogger(HealthController.class);

    @GetMapping("/api/health")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }

    @PostMapping("/api/contact")
    public ResponseEntity<Void> submitContact(@RequestBody Map<String, String> body) {
        log.info("CONTACT SUBMISSION RECEIVED: Name: {}, Email: {}, Message: {}",
                body.get("name"), body.get("email"), body.get("message"));
        return ResponseEntity.ok().build();
    }
}
