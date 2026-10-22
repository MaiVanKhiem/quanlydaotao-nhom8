package com.example.quanlydaotao.n8.repository;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class StudentComponentGradeRepository
        extends N8RepositorySupport {

    private static final String SELECT = """
            SELECT g.*
            FROM dbo.student_component_grades g
            JOIN dbo.course_registrations r
                ON r.id = g.registration_id
            JOIN dbo.grade_components c
                ON c.id = g.component_id
            WHERE r.course_class_id = ?
              AND c.course_class_id = r.course_class_id
              AND r.deleted_at IS NULL
              AND g.is_active = 1
              AND g.deleted_at IS NULL
              AND c.is_active = 1
              AND c.deleted_at IS NULL
            """;

    public StudentComponentGradeRepository(JdbcTemplate jdbc) {
        super(jdbc);
    }

    public List<Map<String, Object>> findAll(UUID classId) {
        requireClass(classId);

        return query(
                SELECT + " ORDER BY g.registration_id, g.component_id",
                classId
        );
    }

    public Map<String, Object> findById(
            UUID classId,
            UUID gradeId
    ) {
        requireClass(classId);

        return one(
                SELECT + " AND g.id = ?",
                classId,
                gradeId
        );
    }
}