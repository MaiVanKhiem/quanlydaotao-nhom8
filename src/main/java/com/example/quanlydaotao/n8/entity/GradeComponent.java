package com.example.quanlydaotao.n8.entity;

import java.math.BigDecimal;
import java.util.UUID;

public record GradeComponent(
        UUID id,
        UUID courseClassId,
        String componentCode,
        String componentName,
        BigDecimal weightPercentage,
        BigDecimal minScore,
        BigDecimal maxScore,
        Boolean isRequired,
        Integer inputOrder,
        AuditFields audit
) {
}