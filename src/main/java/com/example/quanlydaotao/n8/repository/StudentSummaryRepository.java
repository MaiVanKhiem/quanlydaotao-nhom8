package com.example.quanlydaotao.n8.repository;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class StudentSummaryRepository
        extends N8RepositorySupport {

    private static final String SELECT = """
        SELECT ss.*
        FROM dbo.student_summaries ss
        JOIN dbo.course_registrations r
            ON r.id=ss.registration_id
        WHERE r.course_class_id=?
          AND r.deleted_at IS NULL
          AND ss.is_active=1
          AND ss.deleted_at IS NULL
        """;

    public StudentSummaryRepository(JdbcTemplate jdbc) {
        super(jdbc);
    }

    public List<Map<String, Object>> findAll(UUID classId) {
        requireClass(classId);

        return query(
                SELECT + " ORDER BY ss.registration_id",
                classId
        );
    }

    public Map<String, Object> findById(
            UUID classId,
            UUID summaryId
    ) {
        requireClass(classId);

        return one(
                SELECT + " AND ss.id=?",
                classId,
                summaryId
        );
    }
}