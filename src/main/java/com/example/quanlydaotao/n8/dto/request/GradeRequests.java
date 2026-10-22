package com.example.quanlydaotao.n8.dto.request;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public final class GradeRequests {

    private GradeRequests() {
    }

    public record Enrollment(
            List<UUID> student_ids
    ) {
    }

    public record Component(
            UUID id,
            String component_code,
            String component_name,
            BigDecimal weight_percentage,
            BigDecimal min_score,
            BigDecimal max_score,
            Boolean is_required,
            Integer input_order
    ) {
    }

    public record Scale(
            UUID id,
            String scale_code,
            BigDecimal min_score,
            BigDecimal max_score,
            String letter_grade,
            BigDecimal gpa_value,
            Boolean is_pass
    ) {
    }

    public record Grade(
            UUID registration_id,
            UUID component_id,
            BigDecimal score,
            Boolean is_retake,
            String note
    ) {
    }

    public record GradeBatch(
            List<Grade> grades
    ) {
    }
}