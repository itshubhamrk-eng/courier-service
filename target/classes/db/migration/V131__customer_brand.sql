-- A business customer's brand: website and logo (shown in the customer shipping portal). The logo is a data: URL
-- (PNG, JPEG or WebP, at most 150 KB before encoding, checked by the service), same as courier_integrations.logo_data_url.
ALTER TABLE customers
    ADD COLUMN website       VARCHAR(255) NULL COMMENT 'Brand website (http/https URL)',
    ADD COLUMN logo_data_url MEDIUMTEXT   NULL COMMENT 'Brand logo as a data: URL (png/jpeg/webp)';
