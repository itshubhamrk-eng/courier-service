package com.courier.modules.customerportal.integrations.api;

import com.courier.modules.customerportal.integrations.application.CourierTrackingWebhookService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * Where couriers push shipment status updates: {@code POST /api/v1/webhooks/courier-tracking/{token}}. The address is
 * shown to the company administrator in Courier Integrations and pasted into the courier's panel.
 *
 * <p>Public by necessity — couriers call it with no bearer token — so it is listed in
 * {@code SecurityConfig.PUBLIC_ENDPOINTS}. The secret in the address identifies the company and courier; an unknown
 * secret gets 404 and nothing is read. A known one is always answered 200 (couriers retry anything else), even when the
 * shipment is not one of ours or the body cannot be read. The address avoids courier brand names on purpose: Shiprocket
 * refuses webhook URLs that contain its own name.
 */
@RestController
@RequestMapping("/api/v1/webhooks/courier-tracking")
@RequiredArgsConstructor
@Hidden
public class CourierTrackingWebhookController {

    private final CourierTrackingWebhookService service;
    private final ObjectMapper objectMapper;

    /** Some panels check the address with a GET before saving it. */
    @GetMapping("/{token}")
    public ResponseEntity<Map<String, Object>> check(@PathVariable String token) {
        return service.integration(token).<ResponseEntity<Map<String, Object>>>map(i -> ResponseEntity.ok(Map.of("ok", true)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/{token}")
    public ResponseEntity<Map<String, Object>> receive(@PathVariable String token, @RequestBody(required = false) String body) {
        var integration = service.integration(token);
        if (integration.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        JsonNode json;
        try {
            json = body == null || body.isBlank() ? null : objectMapper.readTree(body);
        } catch (JsonProcessingException e) {
            return ResponseEntity.ok(Map.of("ok", true, "matched", 0));
        }
        int matched = service.receive(integration.get(), json, Instant.now());
        return ResponseEntity.ok(Map.of("ok", true, "matched", matched));
    }
}
