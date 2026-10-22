package com.example.quanlydaotao.n8.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record StudentComponentGrade(
        UUID id,
        UUID registrationId,
        UUID componentId,
        BigDecimal score,
        Boolean isRetake,
        Boolean isLocked,
        LocalDateTime gradedAt,
        UUID gradedBy,
        String note,
        AuditFields audit
) {
}