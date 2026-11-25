-- ============================================================================
-- V2 - Phase 1: synchronization foundations.
--
-- Additive only. Every statement either creates a new object or adds a column
-- with a default, so existing rows and existing application code keep working.
-- Nothing is dropped, renamed or rewritten.
--
-- Contents:
--   1. businesses table + the one business BackTrack runs today (id = 1)
--   2. business_id on every business table (tenant column; always 1 for now)
--   3. uuid on every business table (client-generatable identity for sync)
--   4. version on mutable tables (optimistic locking / conflict detection)
--   5. updated_at on sales (they could change before: payments, cancellation)
--   6. indexes for the dashboard, reports and sync scans
--
-- Requires PostgreSQL 13+ for gen_random_uuid().
-- ============================================================================

-- 1. Businesses -------------------------------------------------------------
create table businesses (
    id bigint not null primary key,
    name varchar(150) not null,
    created_at timestamp(6) with time zone not null default now()
);

insert into businesses (id, name, created_at) values (1, 'My Business', now());

-- 2. Tenant column ----------------------------------------------------------
alter table users              add column business_id bigint not null default 1;
alter table customers          add column business_id bigint not null default 1;
alter table products           add column business_id bigint not null default 1;
alter table product_categories add column business_id bigint not null default 1;
alter table sales              add column business_id bigint not null default 1;
alter table sale_items         add column business_id bigint not null default 1;
alter table payments           add column business_id bigint not null default 1;
alter table stock_movements    add column business_id bigint not null default 1;

alter table users              add constraint fk_users_business foreign key (business_id) references businesses (id);
alter table customers          add constraint fk_customers_business foreign key (business_id) references businesses (id);
alter table products           add constraint fk_products_business foreign key (business_id) references businesses (id);
alter table product_categories add constraint fk_product_categories_business foreign key (business_id) references businesses (id);
alter table sales              add constraint fk_sales_business foreign key (business_id) references businesses (id);
alter table sale_items         add constraint fk_sale_items_business foreign key (business_id) references businesses (id);
alter table payments           add constraint fk_payments_business foreign key (business_id) references businesses (id);
alter table stock_movements    add constraint fk_stock_movements_business foreign key (business_id) references businesses (id);

-- 3. Sync identity ----------------------------------------------------------
alter table users              add column uuid varchar(36);
alter table customers          add column uuid varchar(36);
alter table products           add column uuid varchar(36);
alter table product_categories add column uuid varchar(36);
alter table sales              add column uuid varchar(36);
alter table sale_items         add column uuid varchar(36);
alter table payments           add column uuid varchar(36);
alter table stock_movements    add column uuid varchar(36);

update users              set uuid = gen_random_uuid()::text where uuid is null;
update customers          set uuid = gen_random_uuid()::text where uuid is null;
update products           set uuid = gen_random_uuid()::text where uuid is null;
update product_categories set uuid = gen_random_uuid()::text where uuid is null;
update sales              set uuid = gen_random_uuid()::text where uuid is null;
update sale_items         set uuid = gen_random_uuid()::text where uuid is null;
update payments           set uuid = gen_random_uuid()::text where uuid is null;
update stock_movements    set uuid = gen_random_uuid()::text where uuid is null;

alter table users              alter column uuid set not null;
alter table customers          alter column uuid set not null;
alter table products           alter column uuid set not null;
alter table product_categories alter column uuid set not null;
alter table sales              alter column uuid set not null;
alter table sale_items         alter column uuid set not null;
alter table payments           alter column uuid set not null;
alter table stock_movements    alter column uuid set not null;

create unique index ux_users_uuid              on users (uuid);
create unique index ux_customers_uuid          on customers (uuid);
create unique index ux_products_uuid           on products (uuid);
create unique index ux_product_categories_uuid on product_categories (uuid);
create unique index ux_sales_uuid              on sales (uuid);
create unique index ux_sale_items_uuid         on sale_items (uuid);
create unique index ux_payments_uuid           on payments (uuid);
create unique index ux_stock_movements_uuid    on stock_movements (uuid);

-- 4. Optimistic locking on mutable data ------------------------------------
alter table users              add column version bigint not null default 0;
alter table customers          add column version bigint not null default 0;
alter table products           add column version bigint not null default 0;
alter table product_categories add column version bigint not null default 0;
alter table sales              add column version bigint not null default 0;

-- 5. Sales can change after creation (payments, cancellation) --------------
alter table sales add column updated_at timestamp(6) with time zone;

-- 6. Indexes ---------------------------------------------------------------
-- Sales are listed and reported by date.
create index ix_sales_created_at on sales (created_at desc);
-- Foreign keys used by every detail page and report.
create index ix_sale_items_sale on sale_items (sale_id);
create index ix_sale_items_product on sale_items (product_id);
create index ix_payments_sale on payments (sale_id);
create index ix_sales_customer on sales (customer_id);
create index ix_sales_cashier on sales (cashier_id);
create index ix_stock_movements_product on stock_movements (product_id);
create index ix_stock_movements_created_at on stock_movements (created_at desc);
create index ix_products_category on products (category_id);
-- Catalogue filters.
create index ix_products_archived_name on products (archived, name);
create index ix_customers_archived_name on customers (archived, name);
-- Tenant scans (every query will be scoped by business_id in a later phase).
create index ix_sales_business on sales (business_id);
create index ix_products_business on products (business_id);
create index ix_customers_business on customers (business_id);

-- Customer contact numbers are checked for duplicates in Java today. A plain
-- index is added here; the UNIQUE constraint is deliberately postponed to the
-- next phase, because it must first be verified that the live database has no
-- duplicates (a failing migration must never block the system from starting).
create index ix_customers_phone on customers (phone);
