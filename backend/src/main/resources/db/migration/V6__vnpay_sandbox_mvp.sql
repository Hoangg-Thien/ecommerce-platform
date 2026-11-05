ALTER TABLE orders DROP CONSTRAINT orders_payment_method_check;
ALTER TABLE orders ADD CONSTRAINT orders_payment_method_check CHECK (payment_method IN ('COD','MOMO','VNPAY'));
ALTER TABLE payments DROP CONSTRAINT payments_payment_method_check;
ALTER TABLE payments ADD CONSTRAINT payments_payment_method_check CHECK (payment_method IN ('COD','MOMO','VNPAY'));

ALTER TABLE payments
    ADD COLUMN provider_txn_ref VARCHAR(100) UNIQUE,
    ADD COLUMN provider_transaction_no VARCHAR(15),
    ADD COLUMN response_code VARCHAR(2),
    ADD COLUMN transaction_status VARCHAR(2),
    ADD COLUMN paid_at TIMESTAMPTZ,
    ADD COLUMN refund_status VARCHAR(30),
    ADD COLUMN reason VARCHAR(100),
    ADD COLUMN expires_at TIMESTAMPTZ,
    ADD COLUMN payment_url TEXT,
    ADD COLUMN provider_environment VARCHAR(20),
    ADD CONSTRAINT chk_payment_refund_status CHECK (refund_status IN ('NONE','RECONCILIATION_REQUIRED','REQUIRED_MANUAL')),
    ADD CONSTRAINT chk_payment_environment CHECK (provider_environment IN ('SANDBOX'));
