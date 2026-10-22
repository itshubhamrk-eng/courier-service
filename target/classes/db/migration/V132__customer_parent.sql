-- A business customer can register customers of its own (from the customer portal). Each of those carries the id of the
-- customer that registered it; a customer created by company staff has none. A customer keeps its own address list
-- (customer_addresses), so a sub-customer can have many addresses.
ALTER TABLE customers
    ADD COLUMN parent_customer_id BINARY(16) NULL COMMENT 'The customer that registered this one, if any',
    ADD KEY idx_customers_parent (company_id, parent_customer_id),
    ADD CONSTRAINT fk_customers_parent FOREIGN KEY (parent_customer_id) REFERENCES customers (id);
