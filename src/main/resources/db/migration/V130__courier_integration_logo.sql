-- Logo shown to the company's customers next to this courier (rate comparison, bookings). Stored as a
-- data URL (PNG, JPEG or WebP, at most 150 KB before encoding, checked by the service) so the portal can render it without
-- an authenticated image request.
ALTER TABLE courier_integrations
    ADD COLUMN logo_data_url MEDIUMTEXT NULL COMMENT 'Courier logo as a data: URL (png/jpeg/webp)';
