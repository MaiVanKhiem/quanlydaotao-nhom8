
package com.example.quanlydaotao.n8.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Component
public class AuthFilter extends OncePerRequestFilter {

    private final JdbcTemplate jdbc;

    public AuthFilter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest req,
            HttpServletResponse res,
            FilterChain chain)
            throws ServletException, IOException {

        String path = req.getRequestURI();
        String method = req.getMethod();

        // Cho phép trang đăng nhập, đăng ký và tài nguyên của chúng
        boolean publicPath =
                path.equals("/login.html") ||
                path.equals("/register.html") ||
                path.equals("/favicon.ico") ||
                path.startsWith("/css/") ||
                path.startsWith("/images/") ||
                path.equals("/api/auth/login") ||
                path.equals("/api/auth/register");

        if (publicPath) {
            chain.doFilter(req, res);
            return;
        }

        HttpSession session = req.getSession(false);

        if (session == null ||
            session.getAttribute("userId") == null) {
            deny(req, res, 401, "Bạn chưa đăng nhập");
            return;
        }

        UUID userId;

        try {
            userId = UUID.fromString(
                session.getAttribute("userId").toString()
            );
        } catch (IllegalArgumentException e) {
            session.invalidate();
            deny(req, res, 401, "Phiên đăng nhập không hợp lệ");
            return;
        }

        List<String> roles = jdbc.query("""
            SELECT role FROM dbo.app_users WHERE id = ?
            """,
            (rs, index) -> rs.getString("role"),
            userId
        );

        if (roles.isEmpty()) {
            session.invalidate();
            deny(req, res, 401, "Tài khoản không tồn tại");
            return;
        }

        String role = roles.get(0);

        boolean isAdmin = role.equals("ADMIN");
        boolean canEdit = isAdmin || role.equals("EDITOR");

        boolean isWrite =
                method.equals("POST") ||
                method.equals("PUT") ||
                method.equals("PATCH") ||
                method.equals("DELETE");

        // Trang Admin chỉ dành cho quản trị viên
        if (path.equals("/admin.html") && !isAdmin) {
            deny(req, res, 403, "Chỉ Admin được truy cập");
            return;
        }

        // Các API quản trị chỉ dành cho Admin
        if (path.startsWith("/api/admin/") &&
            !path.equals("/api/admin/request-edit") &&
            !isAdmin) {

            deny(req, res, 403, "Bạn không có quyền Admin");
            return;
        }

        // Chặn người dùng thường sửa dữ liệu
        if (path.startsWith("/api/n8/") &&
            isWrite && !canEdit) {

            deny(req, res, 403,
                "Bạn chỉ có quyền xem. Hãy yêu cầu Admin duyệt.");
            return;
        }

        chain.doFilter(req, res);
    }

    private void deny(
            HttpServletRequest req,
            HttpServletResponse res,
            int status,
            String message) throws IOException {

        if (status == 401 &&
            !req.getRequestURI().startsWith("/api/")) {

            res.sendRedirect("/login.html");
            return;
        }

        res.setStatus(status);
        res.setContentType("application/json;charset=UTF-8");
        res.getWriter().write(
            "{\"message\":\"" + message + "\"}"
        );
    }
}
