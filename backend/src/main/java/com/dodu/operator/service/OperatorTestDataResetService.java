package com.dodu.operator.service;

import com.dodu.intervention.PhotoObjectWriter;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Explicit operator-only reset. Does not delete participants, credentials, sessions or subscriptions. */
@Service
public class OperatorTestDataResetService {
    private static final List<String> TABLES = List.of("behavior_event", "photo_evaluation_job", "notification_delivery",
            "validation_survey", "promise_schedule_revision", "promise_creation_request", "auth_attempt",
            "promise", "promise_date_usage", "photo_asset");
    private final JdbcTemplate jdbc;
    private final ObjectProvider<PhotoObjectWriter> writers;

    public OperatorTestDataResetService(JdbcTemplate jdbc, ObjectProvider<PhotoObjectWriter> writers) {
        this.jdbc = jdbc; this.writers = writers;
    }

    @Transactional
    public ResetResult reset() {
        // Fail before touching Storage when an active transaction is using the reset scope.
        jdbc.execute("lock table " + String.join(", ", TABLES) + " in access exclusive mode nowait");
        Set<String> allowed = Set.copyOf(TABLES);
        var references = jdbc.query("""
                select src.relname, dst.relname
                  from pg_constraint c join pg_class src on src.oid = c.conrelid
                  join pg_class dst on dst.oid = c.confrelid
                  join pg_namespace n on n.oid = dst.relnamespace
                 where c.contype = 'f' and n.nspname = current_schema()
                """, (rs, i) -> List.of(rs.getString(1), rs.getString(2)));
        if (references.stream().anyMatch(r -> allowed.contains(r.get(1)) && !allowed.contains(r.get(0)))) {
            throw new IllegalStateException("Reset scope has an unexpected dependent table");
        }
        long participants = count("participant"), credentials = count("access_credential");
        var photos = jdbc.query("select storage_key from photo_asset where deleted_at is null", (rs, i) -> rs.getString(1));
        var writer = writers.getIfAvailable();
        if (!photos.isEmpty() && writer == null) throw new IllegalStateException("Photo storage must be configured before reset");
        for (String photo : photos) writer.delete(photo);
        long promises = count("promise");
        long removed = 0;
        for (String table : TABLES) {
            removed += jdbc.update("delete from " + table + ("behavior_event".equals(table)
                    ? " where promise_id is not null or auth_attempt_id is not null" : ""));
        }
        return new ResetResult(promises, photos.size(), removed, participants, credentials);
    }

    private long count(String table) { return jdbc.queryForObject("select count(*) from " + table, Long.class); }
    public record ResetResult(long promises, long photos, long deletedRows, long participantsKept, long credentialsKept) { }
}
