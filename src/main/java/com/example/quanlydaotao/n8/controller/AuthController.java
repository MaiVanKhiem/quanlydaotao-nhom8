
package com.example.quanlydaotao.n8.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final JdbcTemplate jdbc;
    private final BCryptPasswordEncoder encoder =
            new BCryptPasswordEncoder();

    public AuthController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record RegisterRequest(
            String fullName,
            String email,
            String password
    ) {}

    public record LoginRequest(
            String email,
            String password
    ) {}

    @PostMapping("/register")
    public ResponseEntity<?> register(
            @RequestBody RegisterRequest request) {

        if (request.fullName() == null ||
            request.email() == null ||
            request.password() == null) {
            return ResponseEntity.badRequest().body(
                Map.of("message", "Vui lòng nhập đầy đủ thông tin")
            );
        }

        String fullName = request.fullName().trim();
        String email = request.email()
                .trim()
                .toLowerCase(Locale.ROOT);
        String password = request.password();

        if (fullName.isEmpty() || fullName.length() > 100 ||
            email.length() > 255 ||
            !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$") ||
            password.length() < 8 ||
            password.getBytes(StandardCharsets.UTF_8).length > 72) {

            return ResponseEntity.badRequest().body(
                Map.of("message", "Thông tin đăng ký không hợp lệ")
            );
        }

        try {
            jdbc.update("""
                INSERT INTO dbo.app_users
                (id, full_name, email, password_hash, role, created_at)
                VALUES (?, ?, ?, ?, 'VIEWER', SYSDATETIME())
                """,
                UUID.randomUUID(),
                fullName,
                email,
                encoder.encode(password)
            );
        } catch (DuplicateKeyException e) {
            return ResponseEntity.badRequest().body(
                Map.of("message", "Email đã tồn tại")
            );
        }

        return ResponseEntity.ok(Map.of(
            "message", "Đăng ký thành công",
            "role", "VIEWER"
        ));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(
            @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {

        if (request.email() == null ||
            request.password() == null) {
            return ResponseEntity.badRequest().body(
                Map.of("message", "Thiếu email hoặc mật khẩu")
            );
        }

        String email = request.email()
                .trim()
                .toLowerCase(Locale.ROOT);

        List<Map<String, Object>> users = jdbc.queryForList("""
            SELECT id, full_name, email, password_hash, role
            FROM dbo.app_users
            WHERE email = ?
            """, email);

        if (users.isEmpty() ||
            !encoder.matches(
                request.password(),
                users.get(0).get("password_hash").toString()
            )) {

            return ResponseEntity.status(401).body(
                Map.of("message", "Email hoặc mật khẩu không đúng")
            );
        }

        Map<String, Object> user = users.get(0);

        HttpSession oldSession = httpRequest.getSession(false);
        if (oldSession != null) oldSession.invalidate();

        HttpSession session = httpRequest.getSession(true);
        session.setAttribute(
            "userId",
            user.get("id").toString()
        );

        return ResponseEntity.ok(Map.of(
            "message", "Đăng nhập thành công",
            "fullName", user.get("full_name"),
            "role", user.get("role")
        ));
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(
            HttpServletRequest request) {

        HttpSession session = request.getSession(false);

        if (session == null ||
            session.getAttribute("userId") == null) {
            return ResponseEntity.status(401).body(
                Map.of("message", "Chưa đăng nhập")
            );
        }

        UUID userId;

        try {
            userId = UUID.fromString(
                session.getAttribute("userId").toString()
            );
        } catch (IllegalArgumentException e) {
            session.invalidate();
            return ResponseEntity.status(401).body(
                Map.of("message", "Phiên đăng nhập không hợp lệ")
            );
        }

        List<Map<String, Object>> users = jdbc.queryForList("""
            SELECT full_name, email, role
            FROM dbo.app_users
            WHERE id = ?
            """, userId);

        if (users.isEmpty()) {
            session.invalidate();
            return ResponseEntity.status(401).body(
                Map.of("message", "Tài khoản không tồn tại")
            );
        }

        Map<String, Object> user = users.get(0);

        return ResponseEntity.ok(Map.of(
            "fullName", user.get("full_name"),
            "email", user.get("email"),
            "role", user.get("role")
        ));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(
            HttpServletRequest request) {

        HttpSession session = request.getSession(false);

        if (session != null) {
            session.invalidate();
        }

        return ResponseEntity.ok(
            Map.of("message", "Đăng xuất thành công")
        );
    }
}
