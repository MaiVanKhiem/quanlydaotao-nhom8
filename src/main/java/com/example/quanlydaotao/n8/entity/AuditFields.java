package com.example.quanlydaotao.n8.entity;

import java.time.LocalDateTime;
import java.util.UUID;

public record AuditFields(
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        UUID createdBy,
        UUID updatedBy,
        LocalDateTime deletedAt,
        UUID deletedBy,
        Boolean isActive
) {
}