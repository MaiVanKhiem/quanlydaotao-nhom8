package com.example.quanlydaotao.n8.repository;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class GradeComponentRepository extends N8RepositorySupport {

    public GradeComponentRepository(JdbcTemplate jdbc) {
        super(jdbc);
    }

    public List<Map<String, Object>> findAll(UUID classId) {
        requireClass(classId);

        return query("""
                SELECT *
                FROM dbo.grade_components
                WHERE course_class_id = ?
                  AND is_active = 1
                  AND deleted_at IS NULL
                ORDER BY COALESCE(input_order, 0), component_code
                """, classId);
    }

    public Map<String, Object> findById(
            UUID classId,
            UUID componentId
    ) {
        requireClass(classId);

        return one("""
                SELECT *
                FROM dbo.grade_components
                WHERE course_class_id = ?
                  AND id = ?
                  AND is_active = 1
                  AND deleted_at IS NULL
                """, classId, componentId);
    }
}