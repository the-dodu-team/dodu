package com.dodu.operator.service;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Local operator enrollment only; credentials are returned after the batch commits. */
@Service
public class OperatorBatchEnrollmentService {
    public record Enrollment(String publicCode, UUID participantId,
                             OperatorAccessService.IssuedCredential credential) { }

    private final JdbcTemplate jdbc;
    private final OperatorAccessService access;
    private final Clock clock;
    private final TransactionTemplate transactions;

    public OperatorBatchEnrollmentService(JdbcTemplate jdbc, OperatorAccessService access,
                                         Clock clock, PlatformTransactionManager manager) {
        this.jdbc = jdbc;
        this.access = access;
        this.clock = clock;
        this.transactions = new TransactionTemplate(manager);
    }

    public List<Enrollment> enroll(String studyCode, String prefix, int count, String operator) {
        if (studyCode == null || studyCode.isBlank() || studyCode.length() > 64
                || prefix == null || !prefix.matches("[A-Z0-9][A-Z0-9-]{0,39}")
                || count < 1 || count > 100
                || operator == null || operator.isBlank() || operator.length() > 128) {
            throw new IllegalArgumentException("Invalid batch arguments: study, prefix, count (1..100), operator required");
        }
        return transactions.execute(status -> {
            var now = clock.instant();
            // Serializes batches in this period; never changes its approved dates.
            UUID studyId = jdbc.query("""
                    select id from study_period
                     where code = ? and intervention_ends_at > ? and access_ends_at > ?
                     for update
                    """, rs -> rs.next() ? rs.getObject("id", UUID.class) : null,
                    studyCode, Timestamp.from(now), Timestamp.from(now));
            if (studyId == null) throw new IllegalArgumentException("Study period missing or ended");
            var result = new ArrayList<Enrollment>();
            for (int i = 1; i <= count; i++) {
                String publicCode = prefix + "-" + String.format(java.util.Locale.ROOT, "%03d", i);
                List<UUID> inserted = jdbc.query("""
                        insert into participant(study_period_id, public_code, lifecycle_status, enrolled_at)
                        values (?, ?, 'Active', ?)
                        on conflict (public_code) do nothing returning id
                        """, (rs, row) -> rs.getObject("id", UUID.class),
                        studyId, publicCode, Timestamp.from(now));
                if (inserted.isEmpty()) {
                    UUID existing = jdbc.query("""
                            select id from participant where public_code = ? and study_period_id = ?
                            """, rs -> rs.next() ? rs.getObject("id", UUID.class) : null, publicCode, studyId);
                    if (existing == null) throw new IllegalArgumentException("Participant prefix belongs to another study");
                    result.add(new Enrollment(publicCode, existing, null));
                } else {
                    UUID participantId = inserted.getFirst();
                    result.add(new Enrollment(publicCode, participantId, access.issue(participantId, operator)));
                }
            }
            return List.copyOf(result);
        });
    }
}
