-- V51: convert payment enum columns to varchar so Hibernate derived queries
-- (WHERE status = ?) can bind plain strings without per-parameter casts.
-- Same root cause as V40 curriculum enums.

-- transactions.status (tx_status)
ALTER TABLE public.transactions
    ALTER COLUMN status DROP DEFAULT;

ALTER TABLE public.transactions
    ALTER COLUMN status TYPE varchar(32) USING status::text;

ALTER TABLE public.transactions
    ALTER COLUMN status SET DEFAULT 'PENDING';

ALTER TABLE public.transactions
    DROP CONSTRAINT IF EXISTS chk_transactions_status_values;

ALTER TABLE public.transactions
    ADD CONSTRAINT chk_transactions_status_values
        CHECK (status IN ('PENDING', 'PROCESSING', 'SUCCESS', 'FAILED', 'REFUNDED'));

-- transactions.payment_gateway (payment_gw)
ALTER TABLE public.transactions
    ALTER COLUMN payment_gateway TYPE varchar(32) USING payment_gateway::text;

ALTER TABLE public.transactions
    DROP CONSTRAINT IF EXISTS chk_transactions_payment_gateway_values;

ALTER TABLE public.transactions
    ADD CONSTRAINT chk_transactions_payment_gateway_values
        CHECK (payment_gateway IS NULL OR payment_gateway IN ('PAYOS', 'VNPAY', 'MANUAL', 'SEPAY'));

-- SuccessfulPaymentRepository and native SQL still cast 'SUCCESS'::public.tx_status.
-- After dropping the enum type those casts break — rewrite helpers use text compares.
-- The Java repository already mixes casts; keep type drop only after no remaining refs.

DROP TYPE IF EXISTS public.tx_status;
DROP TYPE IF EXISTS public.payment_gw;
