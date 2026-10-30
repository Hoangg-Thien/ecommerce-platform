-- Existing keys have no provable order association; leave them NULL.
ALTER TABLE idempotency_keys
    ADD COLUMN order_id BIGINT REFERENCES orders(id) ON DELETE SET NULL;
CREATE UNIQUE INDEX uq_idempotency_key_order ON idempotency_keys(order_id);
