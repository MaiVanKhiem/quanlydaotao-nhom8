package com.example.quanlydaotao.n8.repository;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

public abstract class N8RepositorySupport {

    private final JdbcTemplate jdbc;

    protected N8RepositorySupport(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    protected List<Map<String, Object>> query(
            String sql,
            Object... values
    ) {
        Object[] parameters = Arrays.stream(values)
                .map(value -> value instanceof UUID
                        ? value.toString()
                        : value)
                .toArray();

        return jdbc.queryForList(sql, parameters);
    }

    protected Map<String, Object> one(
            String sql,
            Object... values
    ) {
        List<Map<String, Object>> result = query(sql, values);

        if (result.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Không tìm thấy bản ghi."
            );
        }

        if (result.size() != 1) {
            throw new IllegalStateException(
                    "Dữ liệu hiện hành bị trùng."
            );
        }

        return result.get(0);
    }

    protected void requireClass(UUID classId) {
        one("""
                SELECT id
                FROM dbo.courses_classes
                WHERE id = ?
                  AND is_active = 1
                  AND deleted_at IS NULL
                """, classId);
    }
}