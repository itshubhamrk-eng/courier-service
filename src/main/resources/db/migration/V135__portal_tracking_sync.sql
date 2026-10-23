-- Portal bookings remember the courier's latest delivery status, kept fresh by a background sync and by the couriers'
-- webhooks, so the history can show and filter by it. Bookings already booked are synced on the first run.
ALTER TABLE portal_bookings
    ADD COLUMN delivery_status      VARCHAR(30)  NULL COMMENT 'AWAITING_PICKUP, PICKED_UP, IN_TRANSIT, OUT_FOR_DELIVERY, DELIVERED, RETURNED or CANCELLED',
    ADD COLUMN delivery_status_text VARCHAR(150) NULL COMMENT 'The courier''s own wording',
    ADD COLUMN delivery_location    VARCHAR(150) NULL,
    ADD COLUMN delivery_status_at   TIMESTAMP(6) NULL COMMENT 'When the courier reported it',
    ADD COLUMN tracking_synced_at   TIMESTAMP(6) NULL COMMENT 'Last time the courier was asked',
    ADD COLUMN next_tracking_sync_at TIMESTAMP(6) NULL COMMENT 'Due for the next sync; NULL = no longer synced (finished)';

CREATE INDEX idx_portal_bookings_tracking_due ON portal_bookings (next_tracking_sync_at);
CREATE INDEX idx_portal_bookings_delivery ON portal_bookings (company_id, customer_id, delivery_status);

UPDATE portal_bookings SET next_tracking_sync_at = CURRENT_TIMESTAMP(6) WHERE status = 'BOOKED' AND deleted = FALSE;

-- One secret per company and courier, in the webhook address the courier calls with status updates.
ALTER TABLE courier_integrations
    ADD COLUMN webhook_token VARCHAR(64) NULL COMMENT 'Secret part of this company''s webhook address for this courier';
CREATE UNIQUE INDEX uk_courier_integrations_webhook_token ON courier_integrations (webhook_token);
