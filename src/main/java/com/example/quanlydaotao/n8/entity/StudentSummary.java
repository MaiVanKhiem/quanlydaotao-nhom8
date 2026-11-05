package com.example.quanlydaotao.n8.entity;

import java.math.BigDecimal;
import java.util.UUID;

public record StudentSummary(
        UUID id,
        UUID registrationId,
        BigDecimal totalScore,
        UUID scaleId,
        String letterGrade,
        BigDecimal gpaValue,
        String result,
        Boolean isFinalized,
        AuditFields audit
) {
}