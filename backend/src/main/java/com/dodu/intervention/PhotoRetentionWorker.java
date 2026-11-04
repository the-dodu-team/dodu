package com.dodu.intervention;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Deletes expired private photo objects before removing their database metadata. */
@Service
public class PhotoRetentionWorker {
    private static final Duration RETENTION = Duration.ofDays(30);
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final PhotoObjectWriter writer;

    public PhotoRetentionWorker(JdbcTemplate jdbc, Clock clock,
                                ObjectProvider<PhotoObjectWriter> writers) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.writer = writers.getIfAvailable();
    }

    @Scheduled(fixedDelayString = "${dodu.photo-retention.worker-delay-ms:3600000}")
    @Transactional
    public void runScheduled() { processDue(clock.instant()); }

    @Transactional
    public int processDue(Instant now) {
        if (writer == null) return 0;
        List<PhotoRow> rows = jdbc.query("""
                select id, storage_key
                  from photo_asset
                 where delete_due_at <= ? and deleted_at is null
                 order by delete_due_at
                 for update skip locked
                 limit 50
                """, (rs, rowNum) -> new PhotoRow(
                        rs.getObject("id", UUID.class), rs.getString("storage_key")), timestamp(now));
        int deleted = 0;
        for (PhotoRow row : rows) {
            try {
                writer.delete(row.storageKey());
                jdbc.update("update auth_attempt set photo_asset_id = null where photo_asset_id = ?", row.assetId());
                jdbc.update("update photo_asset set deleted_at = ? where id = ? and deleted_at is null",
                        timestamp(now), row.assetId());
                deleted++;
            } catch (RuntimeException ignored) {
                // Keep the row eligible so a transient provider failure can be retried later.
            }
        }
        return deleted;
    }

    public static Instant deleteDueAt(Instant interventionEndsAt) {
        return interventionEndsAt.plus(RETENTION);
    }

    private static Timestamp timestamp(Instant instant) { return Timestamp.from(instant); }

    private record PhotoRow(UUID assetId, String storageKey) { }
}
