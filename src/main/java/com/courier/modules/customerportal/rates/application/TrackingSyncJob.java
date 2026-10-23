package com.courier.modules.customerportal.rates.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Entry point for {@link TrackingSyncService#syncDue}: every two minutes it asks the couriers about the portal shipments
 * that are due (each shipment itself is asked about once an hour, or shortly after a webhook). Kept a thin wrapper so the
 * sync is unit-testable without Spring's scheduler.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TrackingSyncJob {

    private final TrackingSyncService sync;

    @Scheduled(fixedDelayString = "${app.customer-portal.tracking-sync.job-delay-ms:120000}", initialDelayString = "180000")
    public void run() {
        try {
            sync.syncDue(Instant.now());
        } catch (RuntimeException e) {
            log.error("Tracking sync run failed", e);
        }
    }
}
