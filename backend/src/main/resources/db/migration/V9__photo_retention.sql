-- V9: retain private photo objects until 30 days after the intervention ends.
alter table photo_asset
    add column delete_due_at timestamptz,
    add column deleted_at timestamptz;

create index ix_photo_asset_delete_due
    on photo_asset (delete_due_at)
    where deleted_at is null and delete_due_at is not null;
