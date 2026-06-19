-- Sprint 3 payment, order, and enrollment foundation.
-- This migration also reconciles legacy commerce tables that exist in the
-- shared Supabase database but were created before Flyway became authoritative.

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_type type
        JOIN pg_namespace namespace ON namespace.oid = type.typnamespace
        WHERE namespace.nspname = 'public' AND type.typname = 'class_status'
    ) THEN
        CREATE TYPE public.class_status AS ENUM ('upcoming', 'ongoing', 'completed', 'cancelled');
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_type type
        JOIN pg_namespace namespace ON namespace.oid = type.typnamespace
        WHERE namespace.nspname = 'public' AND type.typname = 'enroll_status'
    ) THEN
        CREATE TYPE public.enroll_status AS ENUM ('pending', 'active', 'completed', 'refunded', 'cancelled');
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_type type
        JOIN pg_namespace namespace ON namespace.oid = type.typnamespace
        WHERE namespace.nspname = 'public' AND type.typname = 'risk_level'
    ) THEN
        CREATE TYPE public.risk_level AS ENUM ('low', 'medium', 'high', 'critical');
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_type type
        JOIN pg_namespace namespace ON namespace.oid = type.typnamespace
        WHERE namespace.nspname = 'public' AND type.typname = 'tx_status'
    ) THEN
        CREATE TYPE public.tx_status AS ENUM ('PENDING', 'PROCESSING', 'SUCCESS', 'FAILED', 'REFUNDED');
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_type type
        JOIN pg_namespace namespace ON namespace.oid = type.typnamespace
        WHERE namespace.nspname = 'public' AND type.typname = 'payment_gw'
    ) THEN
        CREATE TYPE public.payment_gw AS ENUM ('PAYOS', 'VNPAY', 'MANUAL');
    END IF;
END
$$;

ALTER TYPE public.payment_gw ADD VALUE IF NOT EXISTS 'SEPAY';

ALTER TABLE public.courses
    ADD COLUMN IF NOT EXISTS access_blocked_at timestamp with time zone,
    ADD COLUMN IF NOT EXISTS access_block_reason text,
    ADD COLUMN IF NOT EXISTS access_blocked_by uuid REFERENCES public.users(id);

CREATE TABLE IF NOT EXISTS public.classes (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    course_id uuid NOT NULL REFERENCES public.courses(id),
    class_name character varying(255) NOT NULL,
    trainer_id uuid REFERENCES public.users(id),
    start_date date,
    end_date date,
    schedule_description text,
    max_students integer NOT NULL DEFAULT 30,
    status public.class_status NOT NULL DEFAULT 'upcoming',
    created_by uuid REFERENCES public.users(id),
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    updated_at timestamp with time zone NOT NULL DEFAULT now(),
    deleted_at timestamp with time zone,
    CONSTRAINT chk_class_dates CHECK (end_date IS NULL OR end_date >= start_date),
    CONSTRAINT chk_classes_max_students_positive CHECK (max_students > 0)
);

CREATE TABLE IF NOT EXISTS public.course_enrollments (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    course_id uuid NOT NULL REFERENCES public.courses(id),
    student_id uuid NOT NULL REFERENCES public.users(id),
    enrollment_date timestamp with time zone NOT NULL DEFAULT now(),
    status public.enroll_status NOT NULL DEFAULT 'active',
    path_data jsonb,
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    updated_at timestamp with time zone NOT NULL DEFAULT now(),
    CONSTRAINT course_enrollments_course_id_student_id_key UNIQUE (course_id, student_id)
);

CREATE TABLE IF NOT EXISTS public.class_enrollments (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    class_id uuid NOT NULL REFERENCES public.classes(id),
    student_id uuid NOT NULL REFERENCES public.users(id),
    enrollment_date timestamp with time zone NOT NULL DEFAULT now(),
    price numeric(12,2) NOT NULL DEFAULT 0,
    status public.enroll_status NOT NULL DEFAULT 'pending',
    risk_level public.risk_level,
    risk_notes text,
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    updated_at timestamp with time zone NOT NULL DEFAULT now(),
    CONSTRAINT class_enrollments_class_id_student_id_key UNIQUE (class_id, student_id),
    CONSTRAINT chk_class_enrollments_price_non_negative CHECK (price >= 0)
);

CREATE TABLE IF NOT EXISTS public.transactions (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id uuid NOT NULL REFERENCES public.users(id),
    course_id uuid REFERENCES public.courses(id),
    class_id uuid REFERENCES public.classes(id),
    amount numeric(12,2) NOT NULL,
    currency character varying(10) NOT NULL DEFAULT 'VND',
    status public.tx_status NOT NULL DEFAULT 'PENDING',
    payment_gateway public.payment_gw,
    gateway_transaction_id character varying(100),
    gateway_response_code character varying(50),
    gateway_response_data jsonb,
    invoice_url text,
    description text,
    paid_at timestamp with time zone,
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    updated_at timestamp with time zone NOT NULL DEFAULT now(),
    CONSTRAINT chk_transactions_amount_non_negative CHECK (amount >= 0)
);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'uq_classes_id_course'
          AND conrelid = 'public.classes'::regclass
    ) THEN
        ALTER TABLE public.classes
            ADD CONSTRAINT uq_classes_id_course UNIQUE (id, course_id);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'chk_classes_max_students_positive'
          AND conrelid = 'public.classes'::regclass
    ) THEN
        ALTER TABLE public.classes
            ADD CONSTRAINT chk_classes_max_students_positive CHECK (max_students > 0);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'chk_class_enrollments_price_non_negative'
          AND conrelid = 'public.class_enrollments'::regclass
    ) THEN
        ALTER TABLE public.class_enrollments
            ADD CONSTRAINT chk_class_enrollments_price_non_negative CHECK (price >= 0);
    END IF;
END
$$;

CREATE TABLE IF NOT EXISTS public.carts (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id uuid NOT NULL REFERENCES public.users(id),
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    updated_at timestamp with time zone NOT NULL DEFAULT now(),
    CONSTRAINT uq_carts_user UNIQUE (user_id)
);

CREATE TABLE IF NOT EXISTS public.cart_items (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    cart_id uuid NOT NULL REFERENCES public.carts(id) ON DELETE CASCADE,
    course_id uuid NOT NULL REFERENCES public.courses(id),
    class_id uuid,
    added_at timestamp with time zone NOT NULL DEFAULT now(),
    CONSTRAINT uq_cart_items_product UNIQUE NULLS NOT DISTINCT (cart_id, course_id, class_id),
    CONSTRAINT fk_cart_items_class_course
        FOREIGN KEY (class_id, course_id)
        REFERENCES public.classes(id, course_id)
);

CREATE TABLE IF NOT EXISTS public.orders (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id uuid NOT NULL REFERENCES public.users(id),
    order_code character varying(40) NOT NULL,
    total_amount numeric(12,2) NOT NULL,
    currency character varying(10) NOT NULL DEFAULT 'VND',
    status character varying(20) NOT NULL DEFAULT 'PENDING',
    expires_at timestamp with time zone NOT NULL,
    paid_at timestamp with time zone,
    cancelled_at timestamp with time zone,
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    updated_at timestamp with time zone NOT NULL DEFAULT now(),
    CONSTRAINT uq_orders_order_code UNIQUE (order_code),
    CONSTRAINT chk_orders_total_amount_non_negative CHECK (total_amount >= 0),
    CONSTRAINT chk_orders_status CHECK (status IN ('PENDING', 'PAID', 'EXPIRED', 'CANCELLED'))
);

CREATE TABLE IF NOT EXISTS public.order_items (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id uuid NOT NULL REFERENCES public.orders(id) ON DELETE RESTRICT,
    course_id uuid NOT NULL REFERENCES public.courses(id),
    class_id uuid,
    item_title character varying(255) NOT NULL,
    unit_price numeric(12,2) NOT NULL,
    discount_amount numeric(12,2) NOT NULL DEFAULT 0,
    final_amount numeric(12,2) NOT NULL,
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    CONSTRAINT fk_order_items_class_course
        FOREIGN KEY (class_id, course_id)
        REFERENCES public.classes(id, course_id),
    CONSTRAINT chk_order_items_unit_price_non_negative CHECK (unit_price >= 0),
    CONSTRAINT chk_order_items_discount_non_negative CHECK (discount_amount >= 0),
    CONSTRAINT chk_order_items_final_amount_non_negative CHECK (final_amount >= 0),
    CONSTRAINT chk_order_items_amounts CHECK (final_amount = unit_price - discount_amount)
);

ALTER TABLE public.transactions
    ADD COLUMN IF NOT EXISTS order_id uuid REFERENCES public.orders(id),
    ADD COLUMN IF NOT EXISTS gateway_event_id bigint,
    ADD COLUMN IF NOT EXISTS invoice_number character varying(40),
    ADD COLUMN IF NOT EXISTS expires_at timestamp with time zone;

ALTER TABLE public.transactions DROP CONSTRAINT IF EXISTS chk_tx_target;

ALTER TABLE public.transactions
    ADD CONSTRAINT chk_tx_target CHECK (
        course_id IS NOT NULL OR class_id IS NOT NULL OR order_id IS NOT NULL
    );

CREATE TABLE IF NOT EXISTS public.sepay_orders (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id uuid NOT NULL REFERENCES public.orders(id),
    transaction_id uuid NOT NULL REFERENCES public.transactions(id),
    payment_code character varying(50) NOT NULL,
    bank_account_number character varying(50) NOT NULL,
    bank_name character varying(100) NOT NULL,
    account_name character varying(255) NOT NULL,
    amount numeric(12,2) NOT NULL,
    qr_url text NOT NULL,
    status character varying(30) NOT NULL DEFAULT 'CREATED',
    expires_at timestamp with time zone NOT NULL,
    matched_at timestamp with time zone,
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    updated_at timestamp with time zone NOT NULL DEFAULT now(),
    CONSTRAINT uq_sepay_orders_order UNIQUE (order_id),
    CONSTRAINT uq_sepay_orders_transaction UNIQUE (transaction_id),
    CONSTRAINT uq_sepay_orders_payment_code UNIQUE (payment_code),
    CONSTRAINT chk_sepay_orders_amount_positive CHECK (amount > 0),
    CONSTRAINT chk_sepay_orders_status CHECK (
        status IN ('CREATED', 'WAITING_PAYMENT', 'MATCHED', 'EXPIRED', 'MISMATCHED')
    )
);

CREATE TABLE IF NOT EXISTS public.sepay_webhook_events (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    gateway_event_id bigint NOT NULL,
    signature text,
    event_timestamp bigint,
    payload jsonb NOT NULL,
    processing_status character varying(30) NOT NULL DEFAULT 'RECEIVED',
    failure_reason text,
    received_at timestamp with time zone NOT NULL DEFAULT now(),
    processed_at timestamp with time zone,
    CONSTRAINT uq_sepay_webhook_events_gateway_event UNIQUE (gateway_event_id),
    CONSTRAINT chk_sepay_webhook_events_status CHECK (
        processing_status IN ('RECEIVED', 'PROCESSED', 'DUPLICATE', 'MISMATCHED', 'FAILED')
    )
);

CREATE TABLE IF NOT EXISTS public.enrollment_status_history (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    course_enrollment_id uuid REFERENCES public.course_enrollments(id),
    class_enrollment_id uuid REFERENCES public.class_enrollments(id),
    student_id uuid NOT NULL REFERENCES public.users(id),
    from_status public.enroll_status,
    to_status public.enroll_status NOT NULL,
    source character varying(30) NOT NULL,
    reason text,
    transaction_id uuid REFERENCES public.transactions(id),
    changed_by uuid REFERENCES public.users(id),
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    CONSTRAINT chk_enrollment_history_target CHECK (
        (course_enrollment_id IS NOT NULL AND class_enrollment_id IS NULL)
        OR (course_enrollment_id IS NULL AND class_enrollment_id IS NOT NULL)
    ),
    CONSTRAINT chk_enrollment_history_source CHECK (
        source IN ('FREE_ENROLLMENT', 'PAYMENT_SUCCESS', 'ADMIN', 'REFUND')
    ),
    CONSTRAINT chk_enrollment_history_payment_reference CHECK (
        source <> 'PAYMENT_SUCCESS' OR transaction_id IS NOT NULL
    )
);

CREATE OR REPLACE FUNCTION public.prevent_enrollment_status_history_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'enrollment_status_history is append-only';
END;
$$;

DROP TRIGGER IF EXISTS trg_enrollment_status_history_append_only
    ON public.enrollment_status_history;
CREATE TRIGGER trg_enrollment_status_history_append_only
    BEFORE UPDATE OR DELETE ON public.enrollment_status_history
    FOR EACH ROW
    EXECUTE FUNCTION public.prevent_enrollment_status_history_mutation();

CREATE SEQUENCE IF NOT EXISTS public.invoice_number_seq AS bigint START WITH 1 INCREMENT BY 1;

CREATE INDEX IF NOT EXISTS idx_classes_course ON public.classes(course_id);
CREATE INDEX IF NOT EXISTS idx_classes_status ON public.classes(status);
CREATE INDEX IF NOT EXISTS idx_course_enrollments_student_status
    ON public.course_enrollments(student_id, status, enrollment_date DESC);
CREATE INDEX IF NOT EXISTS idx_class_enrollments_class_status
    ON public.class_enrollments(class_id, status);
CREATE INDEX IF NOT EXISTS idx_class_enrollments_student_status
    ON public.class_enrollments(student_id, status, enrollment_date DESC);
CREATE INDEX IF NOT EXISTS idx_carts_user ON public.carts(user_id);
CREATE INDEX IF NOT EXISTS idx_cart_items_cart ON public.cart_items(cart_id);
CREATE INDEX IF NOT EXISTS idx_cart_items_course ON public.cart_items(course_id);
CREATE INDEX IF NOT EXISTS idx_cart_items_class ON public.cart_items(class_id) WHERE class_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_orders_user_created ON public.orders(user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_orders_pending_expiry
    ON public.orders(expires_at) WHERE status = 'PENDING';
CREATE INDEX IF NOT EXISTS idx_order_items_order ON public.order_items(order_id);
CREATE INDEX IF NOT EXISTS idx_order_items_course ON public.order_items(course_id);
CREATE INDEX IF NOT EXISTS idx_order_items_class ON public.order_items(class_id) WHERE class_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_transactions_order ON public.transactions(order_id);
CREATE INDEX IF NOT EXISTS idx_transactions_class ON public.transactions(class_id) WHERE class_id IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_transactions_gateway_event
    ON public.transactions(gateway_event_id) WHERE gateway_event_id IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_transactions_gateway_transaction
    ON public.transactions(gateway_transaction_id) WHERE gateway_transaction_id IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_transactions_invoice_number
    ON public.transactions(invoice_number) WHERE invoice_number IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_sepay_orders_status_expiry
    ON public.sepay_orders(status, expires_at);
CREATE INDEX IF NOT EXISTS idx_sepay_webhook_events_status_received
    ON public.sepay_webhook_events(processing_status, received_at);
CREATE INDEX IF NOT EXISTS idx_courses_access_blocked_by
    ON public.courses(access_blocked_by) WHERE access_blocked_by IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_enrollment_history_student_created
    ON public.enrollment_status_history(student_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_enrollment_history_course
    ON public.enrollment_status_history(course_enrollment_id, created_at DESC)
    WHERE course_enrollment_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_enrollment_history_class
    ON public.enrollment_status_history(class_enrollment_id, created_at DESC)
    WHERE class_enrollment_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_enrollment_history_transaction
    ON public.enrollment_status_history(transaction_id)
    WHERE transaction_id IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_enrollment_history_payment_course
    ON public.enrollment_status_history(transaction_id, course_enrollment_id, to_status)
    WHERE source = 'PAYMENT_SUCCESS' AND course_enrollment_id IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_enrollment_history_payment_class
    ON public.enrollment_status_history(transaction_id, class_enrollment_id, to_status)
    WHERE source = 'PAYMENT_SUCCESS' AND class_enrollment_id IS NOT NULL;

ALTER TABLE public.classes ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.course_enrollments ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.class_enrollments ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.transactions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.carts ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.cart_items ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.orders ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.order_items ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.sepay_orders ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.sepay_webhook_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.enrollment_status_history ENABLE ROW LEVEL SECURITY;

REVOKE ALL ON public.carts, public.cart_items, public.orders, public.order_items,
    public.sepay_orders, public.sepay_webhook_events,
    public.enrollment_status_history FROM anon, authenticated;

COMMENT ON TABLE public.orders IS 'Immutable commercial order header after checkout.';
COMMENT ON TABLE public.order_items IS 'Immutable product title and price snapshots captured at checkout.';
COMMENT ON TABLE public.sepay_webhook_events IS 'Raw SePay webhook audit and idempotency boundary; secrets are never stored.';
COMMENT ON TABLE public.enrollment_status_history IS 'Append-only audit of course and class enrollment state transitions.';
