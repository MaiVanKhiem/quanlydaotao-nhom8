
package com.example.quanlydaotao.n8.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final JdbcTemplate jdbc;

    public AdminController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private UUID getUserId(HttpServletRequest request) {
        HttpSession session = request.getSession(false);

        if (session == null ||
            session.getAttribute("userId") == null) {
            throw new IllegalStateException("Chưa đăng nhập");
        }

        return UUID.fromString(
            session.getAttribute("userId").toString()
        );
    }

    private boolean isAdmin(UUID userId) {
        Integer count = jdbc.queryForObject("""
            SELECT COUNT(*) FROM dbo.app_users
            WHERE id = ? AND role = 'ADMIN'
            """, Integer.class, userId);

        return count != null && count > 0;
    }

    private ResponseEntity<?> forbidden() {
        return ResponseEntity.status(403).body(
            Map.of("message", "Bạn không có quyền Admin")
        );
    }

    @PostMapping("/request-edit")
    public ResponseEntity<?> requestEdit(
            HttpServletRequest request) {

        UUID userId = getUserId(request);

        String role = jdbc.queryForObject("""
            SELECT role FROM dbo.app_users WHERE id = ?
            """, String.class, userId);

        if (!"VIEWER".equals(role)) {
            return ResponseEntity.badRequest().body(
                Map.of("message", "Bạn đã có quyền chỉnh sửa")
            );
        }

        Integer pending = jdbc.queryForObject("""
            SELECT COUNT(*) FROM dbo.permission_requests
            WHERE user_id = ? AND status = 'PENDING'
            """, Integer.class, userId);

        if (pending != null && pending > 0) {
            return ResponseEntity.badRequest().body(
                Map.of("message", "Yêu cầu đang chờ Admin duyệt")
            );
        }

        jdbc.update("""
            INSERT INTO dbo.permission_requests
                (id, user_id, status, requested_at)
            VALUES (?, ?, 'PENDING', SYSDATETIME())
            """, UUID.randomUUID(), userId);

        return ResponseEntity.ok(Map.of(
            "message", "Đã gửi yêu cầu quyền chỉnh sửa"
        ));
    }

    @GetMapping("/requests")
    public ResponseEntity<?> getRequests(
            HttpServletRequest request) {

        if (!isAdmin(getUserId(request))) {
            return forbidden();
        }

        return ResponseEntity.ok(jdbc.queryForList("""
            SELECT r.id, r.user_id,
                   u.full_name, u.email,
                   r.status, r.requested_at,
                   r.reviewed_at
            FROM dbo.permission_requests r
            JOIN dbo.app_users u ON u.id = r.user_id
            ORDER BY r.requested_at DESC
            """));
    }

    @GetMapping("/users")
    public ResponseEntity<?> getUsers(
            HttpServletRequest request) {

        if (!isAdmin(getUserId(request))) {
            return forbidden();
        }

        return ResponseEntity.ok(jdbc.queryForList("""
            SELECT id, full_name, email, role, created_at
            FROM dbo.app_users
            ORDER BY created_at DESC
            """));
    }

    @Transactional
    @PostMapping("/requests/{id}/approve")
    public ResponseEntity<?> approve(
            @PathVariable UUID id,
            HttpServletRequest request) {

        UUID adminId = getUserId(request);

        if (!isAdmin(adminId)) {
            return forbidden();
        }

        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT user_id FROM dbo.permission_requests
            WHERE id = ? AND status = 'PENDING'
            """, id);

        if (rows.isEmpty()) {
            return ResponseEntity.badRequest().body(
                Map.of("message", "Yêu cầu không còn chờ duyệt")
            );
        }

        UUID userId = UUID.fromString(
            rows.get(0).get("user_id").toString()
        );

        int updated = jdbc.update("""
            UPDATE dbo.permission_requests
            SET status = 'APPROVED',
                reviewed_at = SYSDATETIME(),
                reviewed_by = ?
            WHERE id = ? AND status = 'PENDING'
            """, adminId, id);

        if (updated != 1) {
            return ResponseEntity.badRequest().body(
                Map.of("message", "Yêu cầu đã được xử lý")
            );
        }

        jdbc.update("""
            UPDATE dbo.app_users
            SET role = 'EDITOR'
            WHERE id = ? AND role = 'VIEWER'
            """, userId);

        return ResponseEntity.ok(Map.of(
            "message", "Đã duyệt quyền chỉnh sửa")
        );
    }
    @PostMapping("/requests/{id}/viewer")
    public ResponseEntity<?> approveViewer(
            @PathVariable UUID id,
            HttpServletRequest request) {

        UUID adminId = getUserId(request);

        if (!isAdmin(adminId)) {
            return forbidden();
        }

        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT user_id
            FROM dbo.permission_requests WITH (UPDLOCK, HOLDLOCK)
            WHERE id = ? AND status = 'PENDING'
            """, id);

        if (rows.isEmpty()) {
            return ResponseEntity.badRequest().body(
                Map.of("message", "Yêu cầu không còn chờ duyệt")
            );
        }

        UUID userId = UUID.fromString(
            rows.get(0).get("user_id").toString()
        );

        int updated = jdbc.update("""
            UPDATE dbo.permission_requests
            SET status = 'APPROVED',
                reviewed_at = SYSDATETIME(),
                reviewed_by = ?
            WHERE id = ? AND status = 'PENDING'
            """, adminId, id);

        if (updated != 1) {
            return ResponseEntity.badRequest().body(
                Map.of("message", "Yêu cầu đã được xử lý")
            );
        }

        jdbc.update("""
            UPDATE dbo.app_users
            SET role = 'VIEWER'
            WHERE id = ? AND role = 'VIEWER'
            """, userId);

        return ResponseEntity.ok(Map.of(
            "message", "Đã duyệt quyền Người xem (VIEWER)"
        ));
    }

    @PostMapping("/requests/{id}/reject")
    public ResponseEntity<?> reject(
            @PathVariable UUID id,
            HttpServletRequest request) {

        UUID adminId = getUserId(request);

        if (!isAdmin(adminId)) {
            return forbidden();
        }

        int updated = jdbc.update("""
            UPDATE dbo.permission_requests
            SET status = 'REJECTED',
                reviewed_at = SYSDATETIME(),
                reviewed_by = ?
            WHERE id = ? AND status = 'PENDING'
            """, adminId, id);

        if (updated == 0) {
            return ResponseEntity.badRequest().body(
                Map.of("message", "Yêu cầu không còn chờ duyệt")
            );
        }

        return ResponseEntity.ok(Map.of(
            "message", "Đã từ chối yêu cầu")
        );
    }

    @PostMapping("/users/{id}/revoke")
    public ResponseEntity<?> revoke(
            @PathVariable UUID id,
            HttpServletRequest request) {

        if (!isAdmin(getUserId(request))) {
            return forbidden();
        }

        int updated = jdbc.update("""
            UPDATE dbo.app_users
            SET role = 'VIEWER'
            WHERE id = ? AND role = 'EDITOR'
            """, id);

        if (updated == 0) {
            return ResponseEntity.badRequest().body(
                Map.of("message", "Không thể thu hồi quyền")
            );
        }

        return ResponseEntity.ok(Map.of(
            "message", "Đã thu hồi quyền chỉnh sửa")
        );
    }
}