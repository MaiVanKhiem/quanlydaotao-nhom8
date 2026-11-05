package com.example.quanlydaotao.n8.repository;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class GradeScaleRepository extends N8RepositorySupport {

    public GradeScaleRepository(JdbcTemplate jdbc) {
        super(jdbc);
    }

    public List<Map<String, Object>> findAll() {
        return query("""
            SELECT *
            FROM dbo.grade_scales
            WHERE is_active=1
              AND deleted_at IS NULL
            ORDER BY min_score
            """);
    }

    public Map<String, Object> findById(UUID scaleId) {
        return one("""
            SELECT *
            FROM dbo.grade_scales
            WHERE id=?
              AND is_active=1
              AND deleted_at IS NULL
            """, scaleId);
    }
}