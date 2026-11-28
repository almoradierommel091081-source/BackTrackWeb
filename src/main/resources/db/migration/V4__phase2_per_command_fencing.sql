-- ============================================================================
-- V4 - Phase 2 corrections: per-command fencing and a tenant-scoped
--      idempotency key.
--
-- V3 created sync_operations with a globally unique op_id. That was wrong in
-- two ways: a business's operation ids must be independent of every other
-- business's, and one row had to carry the identity of the device and the exact
-- content that produced it, so a reused id can be told apart from a true retry.
--
-- This migration therefore:
--   1. keeps a surrogate primary key and scopes the operation id to its
--      business (unique (business_id, op_id));
--   2. adds payload_hash - the fingerprint of device + command type + canonical
--      payload - used to recognise an exact retry.
--
-- No business table is touched and no row of business data is rewritten. The
-- sync_operations table was introduced by V3 and holds no rows on a live
-- system, so the backfilled default below is only a safety net.
-- ============================================================================

-- 1. The operation id is unique inside a business, not across the database ----
alter table sync_operations drop constraint sync_operations_pkey;

alter table sync_operations add column id bigserial;
alter table sync_operations add constraint pk_sync_operations primary key (id);

create unique index ux_sync_operations_business_op on sync_operations (business_id, op_id);

-- 2. Fingerprint of the command, so a reuse with different content is refused -
alter table sync_operations add column payload_hash varchar(64) not null default '';
alter table sync_operations alter column payload_hash drop default;

create index ix_sync_operations_device on sync_operations (device_id);
