-- V52: ensure payment enum columns are varchar.
-- V51 may have been recorded in flyway_schema_history with a mismatched/empty
-- checksum (0) before the real SQL landed. This migration is idempotent.

DO $$
BEGIN
    -- transactions.status
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'transactions'
          AND column_name = 'status'
          AND udt_name = 'tx_status'
    ) THEN
        ALTER TABLE public.transactions ALTER COLUMN status DROP DEFAULT;
        ALTER TABLE public.transactions
            ALTER COLUMN status TYPE varchar(32) USING status::text;
        ALTER TABLE public.transactions
            ALTER COLUMN status SET DEFAULT 'PENDING';
    END IF;

    -- transactions.payment_gateway
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'transactions'
          AND column_name = 'payment_gateway'
          AND udt_name = 'payment_gw'
    ) THEN
        ALTER TABLE public.transactions
            ALTER COLUMN payment_gateway TYPE varchar(32) USING payment_gateway::text;
    END IF;
END
$$;

ALTER TABLE public.transactions
    DROP CONSTRAINT IF EXISTS chk_transactions_status_values;

ALTER TABLE public.transactions
    ADD CONSTRAINT chk_transactions_status_values
        CHECK (status IN ('PENDING', 'PROCESSING', 'SUCCESS', 'FAILED', 'REFUNDED'));

ALTER TABLE public.transactions
    DROP CONSTRAINT IF EXISTS chk_transactions_payment_gateway_values;

ALTER TABLE public.transactions
    ADD CONSTRAINT chk_transactions_payment_gateway_values
        CHECK (
            payment_gateway IS NULL
            OR payment_gateway IN ('PAYOS', 'VNPAY', 'MANUAL', 'SEPAY')
        );

DROP TYPE IF EXISTS public.tx_status;
DROP TYPE IF EXISTS public.payment_gw;
