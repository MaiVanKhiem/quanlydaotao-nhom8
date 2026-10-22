package com.example.quanlydaotao.n8.entity;

import java.math.BigDecimal;
import java.util.UUID;

public record GradeScale(
        UUID id,
        String scaleCode,
        BigDecimal minScore,
        BigDecimal maxScore,
        String letterGrade,
        BigDecimal gpaValue,
        Boolean isPass,
        AuditFields audit
) {
}