alter table public.sepay_orders
    add column transfer_content varchar(255);

update public.sepay_orders
set transfer_content = payment_code
where transfer_content is null;

alter table public.sepay_orders
    alter column transfer_content set not null;
