package com.example.quanlydaotao.n8.service;

import java.math.BigDecimal;
import java.sql.CallableStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import com.example.quanlydaotao.n8.dto.request.GradeRequests.Component;
import com.example.quanlydaotao.n8.dto.request.GradeRequests.Enrollment;
import com.example.quanlydaotao.n8.dto.request.GradeRequests.Grade;
import com.example.quanlydaotao.n8.dto.request.GradeRequests.GradeBatch;
import com.example.quanlydaotao.n8.dto.request.GradeRequests.Scale;
import com.example.quanlydaotao.n8.util.GradeCalculator;

@Service
public class GradeService {

    private final JdbcTemplate jdbc;

    public GradeService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ================= JDBC VÀ KIỂM TRA DỮ LIỆU =================

    private Object[] args(Object... values) {
        return Arrays.stream(values)
                .map(value -> value instanceof UUID
                        ? value.toString()
                        : value)
                .toArray();
    }

    private List<Map<String, Object>> query(
            String sql,
            Object... values
    ) {
        return jdbc.queryForList(sql, args(values));
    }

    private int update(String sql, Object... values) {
        return jdbc.update(sql, args(values));
    }

    private long count(String sql, Object... values) {
        Number result = jdbc.queryForObject(
                sql,
                Long.class,
                args(values)
        );

        return result == null ? 0 : result.longValue();
    }

    private Map<String, Object> optional(
            String sql,
            Object... values
    ) {
        var rows = query(sql, values);

        if (rows.size() > 1) {
            throw new IllegalStateException(
                    "Dữ liệu hiện hành bị trùng."
            );
        }

        return rows.isEmpty() ? null : rows.get(0);
    }

    private Map<String, Object> one(
            String sql,
            Object... values
    ) {
        var row = optional(sql, values);

        if (row == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Không tìm thấy dữ liệu."
            );
        }

        return row;
    }

    private static void check(boolean valid, String message) {
        if (!valid) {
            throw new IllegalArgumentException(message);
        }
    }

    private static boolean yes(Object value) {
        return value != null
                && (
                    value.equals(true)
                    || value.toString().equals("1")
                    || value.toString().equalsIgnoreCase("true")
                );
    }

    private static UUID id(Object value) {
        return UUID.fromString(value.toString());
    }

    private static BigDecimal number(
            Object value,
            String fallback
    ) {
        return new BigDecimal(
                value == null ? fallback : value.toString()
        );
    }

    private static String text(
            String value,
            int max,
            String field
    ) {
        String result = value == null ? "" : value.trim();

        check(
                !result.isEmpty() && result.length() <= max,
                field + " phải có từ 1 đến " + max + " ký tự."
        );

        return result;
    }

    private static BigDecimal decimal(
            BigDecimal value,
            BigDecimal min,
            BigDecimal max,
            String field
    ) {
        check(
                value != null
                        && value.compareTo(min) >= 0
                        && value.compareTo(max) <= 0,
                field + " phải nằm trong " + min + "–" + max
        );

        check(
                value.stripTrailingZeros().scale() <= 2,
                field + " chỉ được tối đa 2 số lẻ."
        );

        return value;
    }

    // ================= KHÓA GIAO DỊCH SQL =================

    private void lock() {
        if (!TransactionSynchronizationManager
                .isActualTransactionActive()) {
            throw new IllegalStateException(
                    "Thao tác ghi phải chạy trong "
                    + "@Transactional của Spring."
            );
        }

        Integer result = jdbc.execute(
                (ConnectionCallback<Integer>) connection -> {
                    if (connection.getAutoCommit()) {
                        throw new IllegalStateException(
                                "Kết nối SQL chưa tham gia giao dịch."
                        );
                    }

                    /*
                     * SELECT có FROM giúp bắt đầu giao dịch ngầm
                     * trên kết nối JDBC của SQL Server.
                     *
                     * Không tự BEGIN TRANSACTION, commit hoặc rollback.
                     * Spring quản lý vòng đời giao dịch.
                     */
                    try (
                        Statement statement = connection.createStatement();
                        ResultSet ignored = statement.executeQuery(
                                "SELECT TOP (1) id "
                                + "FROM dbo.courses_classes"
                        )
                    ) {
                        // Chỉ cần thực thi, không cần đọc dữ liệu.
                    }

                    try (
                        Statement statement = connection.createStatement();
                        ResultSet resultSet = statement.executeQuery(
                                "SELECT @@TRANCOUNT"
                        )
                    ) {
                        if (!resultSet.next()
                                || resultSet.getInt(1) < 1) {
                            throw new IllegalStateException(
                                    "SQL Server chưa mở giao dịch. "
                                    + "Kiểm tra cấu hình transaction manager."
                            );
                        }
                    }

                    try (
                        CallableStatement call = connection.prepareCall(
                                "{? = call sys.sp_getapplock(?, ?, ?, ?, ?)}"
                        )
                    ) {
                        call.registerOutParameter(1, Types.INTEGER);

                        call.setNString(2, "n8.grading");
                        call.setString(3, "Exclusive");
                        call.setString(4, "Transaction");
                        call.setInt(5, 15000);
                        call.setNString(6, "public");

                        call.execute();

                        int code = call.getInt(1);

                        if (call.wasNull()) {
                            throw new IllegalStateException(
                                    "SQL Server không trả kết quả lấy khóa."
                            );
                        }

                        return code;
                    }
                }
        );

        if (result == null) {
            throw new IllegalStateException(
                    "Thiếu kết quả lấy khóa SQL."
            );
        }

        if (result >= 0) {
            return;
        }

        String message = switch (result) {
            case -1 ->
                    "Quá 15 giây chờ khóa. Có giao dịch khác "
                    + "đang giữ khóa n8.grading.";
            case -2 ->
                    "Yêu cầu lấy khóa SQL bị hủy.";
            case -3 ->
                    "Giao dịch gặp deadlock. "
                    + "Tải lại dữ liệu rồi thử lại.";
            case -999 ->
                    "SQL từ chối lệnh lấy khóa. "
                    + "Kiểm tra giao dịch và quyền tài khoản SQL.";
            default ->
                    "Không lấy được khóa SQL.";
        };

        throw new ResponseStatusException(
                result == -999
                        ? HttpStatus.INTERNAL_SERVER_ERROR
                        : HttpStatus.CONFLICT,
                message + " [sp_getapplock=" + result + "]"
        );
    }

    // ================= THÔNG TIN VÀ TRẠNG THÁI LỚP =================

    private Map<String, Object> classInfo(UUID classId) {
        return one("""
                SELECT
                    cc.*,
                    c.code AS course_code,
                    c.name AS course_name,
                    c.credits,
                    s.name AS semester_name
                FROM dbo.courses_classes cc
                JOIN dbo.courses c
                    ON c.id = cc.course_id
                LEFT JOIN dbo.semesters s
                    ON s.id = cc.semester_id
                WHERE cc.id = ?
                  AND cc.is_active = 1
                  AND cc.deleted_at IS NULL
                """, classId);
    }

    private void editable(UUID classId) {
        classInfo(classId);

        check(
                count("""
                        SELECT COUNT_BIG(*)
                        FROM dbo.student_summaries ss
                        JOIN dbo.course_registrations r
                            ON r.id = ss.registration_id
                        WHERE r.course_class_id = ?
                          AND r.deleted_at IS NULL
                          AND ss.is_active = 1
                          AND ss.deleted_at IS NULL
                          AND ss.is_finalized = 1
                        """, classId) == 0,
                "Lớp đã chốt điểm. Hãy mở điểm trước."
        );

        check(
                count("""
                        SELECT COUNT_BIG(*)
                        FROM dbo.student_component_grades g
                        JOIN dbo.course_registrations r
                            ON r.id = g.registration_id
                        WHERE r.course_class_id = ?
                          AND r.deleted_at IS NULL
                          AND g.is_active = 1
                          AND g.deleted_at IS NULL
                          AND g.is_locked = 1
                        """, classId) == 0,
                "Lớp còn ô điểm bị khóa. Hãy mở điểm trước."
        );
    }

    private void invalidateRegistration(UUID registrationId) {
        update("""
                UPDATE dbo.student_summaries
                SET is_active = 0,
                    deleted_at = SYSDATETIME(),
                    updated_at = SYSDATETIME()
                WHERE registration_id = ?
                  AND is_active = 1
                  AND deleted_at IS NULL
                  AND is_finalized = 0
                """, registrationId);
    }

    private void invalidateClass(UUID classId) {
        update("""
                UPDATE ss
                SET is_active = 0,
                    deleted_at = SYSDATETIME(),
                    updated_at = SYSDATETIME()
                FROM dbo.student_summaries ss
                JOIN dbo.course_registrations r
                    ON r.id = ss.registration_id
                WHERE r.course_class_id = ?
                  AND ss.is_active = 1
                  AND ss.deleted_at IS NULL
                  AND ss.is_finalized = 0
                """, classId);
    }

    // ================= LỚP VÀ TÌM SINH VIÊN =================

    public List<Map<String, Object>> classes() {
        return query("""
                SELECT
                    cc.id,
                    cc.class_code,
                    cc.course_id,
                    cc.semester_id,
                    cc.max_students,
                    c.code AS course_code,
                    c.name AS course_name,
                    c.credits,
                    s.name AS semester_name,
                    (
                        SELECT COUNT(*)
                        FROM dbo.course_registrations r
                        WHERE r.course_class_id = cc.id
                          AND r.deleted_at IS NULL
                    ) AS student_count
                FROM dbo.courses_classes cc
                JOIN dbo.courses c
                    ON c.id = cc.course_id
                LEFT JOIN dbo.semesters s
                    ON s.id = cc.semester_id
                WHERE cc.is_active = 1
                  AND cc.deleted_at IS NULL
                ORDER BY cc.class_code
                """);
    }

    public List<Map<String, Object>> students(String keyword) {
        String k = keyword == null
                ? ""
                : keyword.trim()
                        .replace("{", "")
                        .replace("}", "")
                        .replace('đ', 'd')
                        .replace('Đ', 'D');

        check(k.length() <= 100, "Từ khóa tối đa 100 ký tự.");

        String[] tokens = k.isBlank()
                ? new String[0]
                : k.split("\\s+");

        check(
                tokens.length <= 12,
                "Từ khóa có quá nhiều phần."
        );

        StringBuilder sql = new StringBuilder("""
                SELECT TOP (100)
                    s.id,
                    s.code,
                    s.full_name
                FROM dbo.students s
                WHERE s.deleted_at IS NULL
                  AND COALESCE(s.is_active, 1) = 1
                """);

        List<Object> parameters = new ArrayList<>();

        for (String token : tokens) {
            String pattern = "%" + token
                    .replace("[", "[[]")
                    .replace("%", "[%]")
                    .replace("_", "[_]") + "%";

            sql.append("""
                    AND (
                        REPLACE(
                            REPLACE(s.code, N'đ', N'd'),
                            N'Đ', N'D'
                        ) COLLATE Latin1_General_100_CI_AI LIKE ?
                        OR
                        REPLACE(
                            REPLACE(s.full_name, N'đ', N'd'),
                            N'Đ', N'D'
                        ) COLLATE Latin1_General_100_CI_AI LIKE ?
                        OR
                        CONVERT(varchar(36), s.id)
                            COLLATE Latin1_General_100_CI_AI LIKE ?
                    )
                    """);

            parameters.add(pattern);
            parameters.add(pattern);
            parameters.add(pattern);
        }

        sql.append(" ORDER BY s.code");

        return query(sql.toString(), parameters.toArray());
    }

    private List<Map<String, Object>> registrations(UUID classId) {
        return query("""
                SELECT
                    r.id AS registration_id,
                    s.id AS student_id,
                    s.code,
                    s.full_name
                FROM dbo.course_registrations r
                JOIN dbo.students s
                    ON s.id = r.student_id
                WHERE r.course_class_id = ?
                  AND r.deleted_at IS NULL
                ORDER BY s.code
                """, classId);
    }

    public Map<String, Object> board(UUID classId) {
        return Map.of(
                "info", classInfo(classId),
                "students", registrations(classId),
                "components", components(classId),
                "grades", grades(classId),
                "summaries", summaries(classId)
        );
    }

    // ================= THÊM / BỎ SINH VIÊN =================

    @Transactional
    public void enroll(UUID classId, Enrollment request) {
        lock();
        editable(classId);

        check(
                request != null
                        && request.student_ids() != null
                        && !request.student_ids().isEmpty()
                        && request.student_ids().size() <= 100,
                "Chọn từ 1 đến 100 sinh viên."
        );

        for (UUID studentId :
                new LinkedHashSet<>(request.student_ids())) {

            check(studentId != null, "Thiếu ID sinh viên.");

            one("""
                    SELECT id
                    FROM dbo.students
                    WHERE id = ?
                      AND deleted_at IS NULL
                      AND COALESCE(is_active, 1) = 1
                    """, studentId);

            long existing = count("""
                    SELECT COUNT_BIG(*)
                    FROM dbo.course_registrations
                    WHERE course_class_id = ?
                      AND student_id = ?
                      AND deleted_at IS NULL
                    """, classId, studentId);

            if (existing > 0) {
                continue;
            }

            int max = number(
                    classInfo(classId).get("max_students"),
                    "0"
            ).intValue();

            check(
                    max <= 0 || registrations(classId).size() < max,
                    "Lớp đã đủ số lượng sinh viên."
            );

            update("""
                    INSERT dbo.course_registrations (
                        id,
                        student_id,
                        course_class_id,
                        registration_type,
                        registered_at,
                        status,
                        is_paid,
                        created_at
                    )
                    VALUES (
                        ?, ?, ?, 0, SYSDATETIME(), 1, 0, SYSDATETIME()
                    )
                    """, UUID.randomUUID(), studentId, classId);
        }
    }

    @Transactional
    public void removeRegistration(
            UUID classId,
            UUID registrationId
    ) {
        lock();
        editable(classId);

        one("""
                SELECT id
                FROM dbo.course_registrations
                WHERE id = ?
                  AND course_class_id = ?
                  AND deleted_at IS NULL
                """, registrationId, classId);

        check(
                count("""
                        SELECT COUNT_BIG(*)
                        FROM dbo.student_component_grades
                        WHERE registration_id = ?
                          AND is_active = 1
                          AND deleted_at IS NULL
                        """, registrationId) == 0,
                "Sinh viên đã có điểm. "
                + "Hãy xử lý điểm trước khi bỏ khỏi lớp."
        );

        invalidateRegistration(registrationId);

        update("""
                UPDATE dbo.course_registrations
                SET deleted_at = SYSDATETIME(),
                    updated_at = SYSDATETIME()
                WHERE id = ?
                """, registrationId);
    }

    // ================= THÀNH PHẦN ĐIỂM =================

    public List<Map<String, Object>> components(UUID classId) {
        classInfo(classId);

        return query("""
                SELECT *
                FROM dbo.grade_components
                WHERE course_class_id = ?
                  AND is_active = 1
                  AND deleted_at IS NULL
                ORDER BY COALESCE(input_order, 0), component_code
                """, classId);
    }

    @Transactional
    public UUID saveComponent(UUID classId, Component request) {
        lock();
        editable(classId);

        check(request != null, "Thiếu nội dung thành phần.");

        String code = text(
                request.component_code(),
                20,
                "Mã thành phần"
        ).toUpperCase(Locale.ROOT);

        check(
                code.matches("[A-Z0-9_+-]+"),
                "Mã thành phần chỉ dùng chữ không dấu, số, _, +, -."
        );

        String name = text(
                request.component_name(),
                50,
                "Tên thành phần"
        );

        BigDecimal weight = decimal(
                request.weight_percentage(),
                BigDecimal.ZERO,
                new BigDecimal("100"),
                "Trọng số"
        );

        BigDecimal max = decimal(
                request.max_score() == null
                        ? BigDecimal.TEN
                        : request.max_score(),
                new BigDecimal("0.01"),
                BigDecimal.TEN,
                "Điểm tối đa"
        );

        BigDecimal min = decimal(
                request.min_score() == null
                        ? BigDecimal.ZERO
                        : request.min_score(),
                BigDecimal.ZERO,
                max,
                "Điểm tối thiểu"
        );

        int order = request.input_order() == null
                ? 1
                : request.input_order();

        check(
                order >= 0 && order <= 1000,
                "Thứ tự phải từ 0 đến 1000."
        );

        UUID componentId = request.id() == null
                ? UUID.randomUUID()
                : request.id();

        if (request.id() != null) {
            one("""
                    SELECT id
                    FROM dbo.grade_components
                    WHERE id = ?
                      AND course_class_id = ?
                      AND is_active = 1
                      AND deleted_at IS NULL
                    """, componentId, classId);
        }

        check(
                count("""
                        SELECT COUNT_BIG(*)
                        FROM dbo.grade_components
                        WHERE course_class_id = ?
                          AND component_code = ?
                          AND id <> ?
                          AND is_active = 1
                          AND deleted_at IS NULL
                        """, classId, code, componentId) == 0,
                "Mã thành phần đã tồn tại trong lớp."
        );

        check(
                count("""
                        SELECT COUNT_BIG(*)
                        FROM dbo.student_component_grades
                        WHERE component_id = ?
                          AND is_active = 1
                          AND deleted_at IS NULL
                          AND score > ?
                        """, componentId, max) == 0,
                "Điểm tối đa mới thấp hơn điểm đã nhập."
        );

        boolean required = request.is_required() == null
                || request.is_required();

        if (request.id() == null) {
            update("""
                    INSERT dbo.grade_components (
                        id,
                        course_class_id,
                        component_code,
                        component_name,
                        weight_percentage,
                        min_score,
                        max_score,
                        is_required,
                        input_order,
                        is_active,
                        created_at
                    )
                    VALUES (
                        ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, SYSDATETIME()
                    )
                    """,
                    componentId,
                    classId,
                    code,
                    name,
                    weight,
                    min,
                    max,
                    required,
                    order
            );
        } else {
            update("""
                    UPDATE dbo.grade_components
                    SET component_code = ?,
                        component_name = ?,
                        weight_percentage = ?,
                        min_score = ?,
                        max_score = ?,
                        is_required = ?,
                        input_order = ?,
                        updated_at = SYSDATETIME()
                    WHERE id = ?
                    """,
                    code,
                    name,
                    weight,
                    min,
                    max,
                    required,
                    order,
                    componentId
            );
        }

        invalidateClass(classId);
        return componentId;
    }

    @Transactional
    public void deleteComponent(UUID classId, UUID componentId) {
        lock();
        editable(classId);

        one("""
                SELECT id
                FROM dbo.grade_components
                WHERE id = ?
                  AND course_class_id = ?
                  AND is_active = 1
                  AND deleted_at IS NULL
                """, componentId, classId);

        update("""
                UPDATE dbo.student_component_grades
                SET is_active = 0,
                    deleted_at = SYSDATETIME(),
                    updated_at = SYSDATETIME()
                WHERE component_id = ?
                  AND is_active = 1
                  AND deleted_at IS NULL
                """, componentId);

        update("""
                UPDATE dbo.grade_components
                SET is_active = 0,
                    deleted_at = SYSDATETIME(),
                    updated_at = SYSDATETIME()
                WHERE id = ?
                """, componentId);

        invalidateClass(classId);
    }

    // ================= THANG ĐIỂM =================

    public List<Map<String, Object>> scales() {
        return query("""
                SELECT *
                FROM dbo.grade_scales
                WHERE is_active = 1
                  AND deleted_at IS NULL
                ORDER BY min_score
                """);
    }

    private void editableScales() {
        check(
                count("""
                        SELECT COUNT_BIG(*)
                        FROM dbo.student_summaries
                        WHERE is_active = 1
                          AND deleted_at IS NULL
                          AND is_finalized = 1
                        """) == 0,
                "Đã có tổng kết chốt dùng thang điểm. "
                + "Phải mở các tổng kết trước khi đổi thang."
        );
    }

    private void invalidateAll() {
        update("""
                UPDATE dbo.student_summaries
                SET is_active = 0,
                    deleted_at = SYSDATETIME(),
                    updated_at = SYSDATETIME()
                WHERE is_active = 1
                  AND deleted_at IS NULL
                  AND is_finalized = 0
                """);
    }

    @Transactional
    public UUID saveScale(Scale request) {
        lock();
        editableScales();

        check(request != null, "Thiếu nội dung thang điểm.");

        String code = text(
                request.scale_code(),
                10,
                "Mã thang"
        ).toUpperCase(Locale.ROOT);

        String letter = text(
                request.letter_grade(),
                2,
                "Điểm chữ"
        ).toUpperCase(Locale.ROOT);

        check(
                code.matches("[A-Z0-9_+-]+")
                        && letter.matches("[A-Z+-]+"),
                "Mã thang và điểm chữ phải dùng ký tự không dấu."
        );

        BigDecimal min = decimal(
                request.min_score(),
                BigDecimal.ZERO,
                BigDecimal.TEN,
                "Cận dưới"
        );

        BigDecimal max = decimal(
                request.max_score(),
                min,
                BigDecimal.TEN,
                "Cận trên"
        );

        BigDecimal gpa = decimal(
                request.gpa_value(),
                BigDecimal.ZERO,
                new BigDecimal("4"),
                "Điểm hệ 4"
        );

        check(
                request.is_pass() != null,
                "Phải chọn trạng thái đạt hoặc không đạt."
        );

        UUID scaleId = request.id() == null
                ? UUID.randomUUID()
                : request.id();

        if (request.id() != null) {
            one("""
                    SELECT id
                    FROM dbo.grade_scales
                    WHERE id = ?
                      AND is_active = 1
                      AND deleted_at IS NULL
                    """, scaleId);
        }

        check(
                count("""
                        SELECT COUNT_BIG(*)
                        FROM dbo.grade_scales
                        WHERE id <> ?
                          AND is_active = 1
                          AND deleted_at IS NULL
                          AND (
                              scale_code = ?
                              OR (
                                  min_score <= ?
                                  AND max_score >= ?
                              )
                          )
                        """, scaleId, code, max, min) == 0,
                "Mã thang đã có hoặc khoảng điểm bị chồng."
        );

        if (request.id() == null) {
            update("""
                    INSERT dbo.grade_scales (
                        id,
                        scale_code,
                        min_score,
                        max_score,
                        letter_grade,
                        gpa_value,
                        is_pass,
                        is_active,
                        created_at
                    )
                    VALUES (
                        ?, ?, ?, ?, ?, ?, ?, 1, SYSDATETIME()
                    )
                    """,
                    scaleId,
                    code,
                    min,
                    max,
                    letter,
                    gpa,
                    request.is_pass()
            );
        } else {
            update("""
                    UPDATE dbo.grade_scales
                    SET scale_code = ?,
                        min_score = ?,
                        max_score = ?,
                        letter_grade = ?,
                        gpa_value = ?,
                        is_pass = ?,
                        updated_at = SYSDATETIME()
                    WHERE id = ?
                    """,
                    code,
                    min,
                    max,
                    letter,
                    gpa,
                    request.is_pass(),
                    scaleId
            );
        }

        invalidateAll();
        return scaleId;
    }

    @Transactional
    public void deleteScale(UUID scaleId) {
        lock();
        editableScales();

        one("""
                SELECT id
                FROM dbo.grade_scales
                WHERE id = ?
                  AND is_active = 1
                  AND deleted_at IS NULL
                """, scaleId);

        update("""
                UPDATE dbo.grade_scales
                SET is_active = 0,
                    deleted_at = SYSDATETIME(),
                    updated_at = SYSDATETIME()
                WHERE id = ?
                """, scaleId);

        invalidateAll();
    }

    // ================= ĐIỂM SINH VIÊN =================

    public List<Map<String, Object>> grades(UUID classId) {
        classInfo(classId);

        return query("""
                SELECT g.*
                FROM dbo.student_component_grades g
                JOIN dbo.course_registrations r
                    ON r.id = g.registration_id
                JOIN dbo.grade_components c
                    ON c.id = g.component_id
                WHERE r.course_class_id = ?
                  AND c.course_class_id = r.course_class_id
                  AND r.deleted_at IS NULL
                  AND g.is_active = 1
                  AND g.deleted_at IS NULL
                  AND c.is_active = 1
                  AND c.deleted_at IS NULL
                """, classId);
    }

    @Transactional
    public void saveGrades(UUID classId, GradeBatch request) {
        lock();
        editable(classId);

        check(
                request != null
                        && request.grades() != null
                        && !request.grades().isEmpty()
                        && request.grades().size() <= 10000,
                "Gửi từ 1 đến 10000 ô điểm."
        );

        Set<String> seen = new HashSet<>();

        for (Grade grade : request.grades()) {
            check(
                    grade != null
                            && grade.registration_id() != null
                            && grade.component_id() != null,
                    "Ô điểm thiếu ID."
            );

            check(
                    seen.add(
                            grade.registration_id()
                            + ":" + grade.component_id()
                    ),
                    "Một ô điểm xuất hiện hai lần trong yêu cầu."
            );

            saveGrade(classId, grade);
        }
    }

    private void saveGrade(UUID classId, Grade request) {
        one("""
                SELECT id
                FROM dbo.course_registrations
                WHERE id = ?
                  AND course_class_id = ?
                  AND deleted_at IS NULL
                """, request.registration_id(), classId);

        var component = one("""
                SELECT *
                FROM dbo.grade_components
                WHERE id = ?
                  AND course_class_id = ?
                  AND is_active = 1
                  AND deleted_at IS NULL
                """, request.component_id(), classId);

        var old = optional("""
                SELECT *
                FROM dbo.student_component_grades
                WHERE registration_id = ?
                  AND component_id = ?
                  AND is_active = 1
                  AND deleted_at IS NULL
                """,
                request.registration_id(),
                request.component_id()
        );

        check(
                old == null || !yes(old.get("is_locked")),
                "Ô điểm đã khóa."
        );

        String note = request.note() == null
                ? null
                : request.note().trim();

        check(
                note == null || note.length() <= 255,
                "Ghi chú tối đa 255 ký tự."
        );

        if (request.score() == null) {
            if (old != null) {
                update("""
                        UPDATE dbo.student_component_grades
                        SET is_active = 0,
                            deleted_at = SYSDATETIME(),
                            updated_at = SYSDATETIME()
                        WHERE id = ?
                        """, id(old.get("id")));
            }
        } else {
            BigDecimal score = decimal(
                    request.score(),
                    BigDecimal.ZERO,
                    number(component.get("max_score"), "10"),
                    "Điểm"
            );

            if (old == null) {
                update("""
                        INSERT dbo.student_component_grades (
                            id,
                            registration_id,
                            component_id,
                            score,
                            is_retake,
                            is_locked,
                            graded_at,
                            note,
                            is_active,
                            created_at
                        )
                        VALUES (
                            ?, ?, ?, ?, ?, 0,
                            SYSDATETIME(), ?, 1, SYSDATETIME()
                        )
                        """,
                        UUID.randomUUID(),
                        request.registration_id(),
                        request.component_id(),
                        score,
                        Boolean.TRUE.equals(request.is_retake()),
                        note
                );
            } else {
                update("""
                        UPDATE dbo.student_component_grades
                        SET score = ?,
                            is_retake = ?,
                            note = ?,
                            graded_at = SYSDATETIME(),
                            updated_at = SYSDATETIME()
                        WHERE id = ?
                        """,
                        score,
                        Boolean.TRUE.equals(request.is_retake()),
                        note,
                        id(old.get("id"))
                );
            }
        }

        invalidateRegistration(request.registration_id());
    }

    private Map<String, Object> gradeInClass(
            UUID classId,
            UUID gradeId
    ) {
        return one("""
                SELECT g.*
                FROM dbo.student_component_grades g
                JOIN dbo.course_registrations r
                    ON r.id = g.registration_id
                WHERE g.id = ?
                  AND r.course_class_id = ?
                  AND r.deleted_at IS NULL
                  AND g.is_active = 1
                  AND g.deleted_at IS NULL
                """, gradeId, classId);
    }

    @Transactional
    public void updateGrade(
            UUID classId,
            UUID gradeId,
            Grade request
    ) {
        lock();
        editable(classId);

        var old = gradeInClass(classId, gradeId);

        check(
                request != null
                        && id(old.get("registration_id"))
                                .equals(request.registration_id())
                        && id(old.get("component_id"))
                                .equals(request.component_id()),
                "Không được đổi sinh viên hoặc thành phần của ô điểm."
        );

        saveGrade(classId, request);
    }

    @Transactional
    public void deleteGrade(UUID classId, UUID gradeId) {
        lock();
        editable(classId);

        var old = gradeInClass(classId, gradeId);

        check(
                !yes(old.get("is_locked")),
                "Ô điểm đã khóa."
        );

        update("""
                UPDATE dbo.student_component_grades
                SET is_active = 0,
                    deleted_at = SYSDATETIME(),
                    updated_at = SYSDATETIME()
                WHERE id = ?
                """, gradeId);

        invalidateRegistration(
                id(old.get("registration_id"))
        );
    }

    // ================= TỔNG KẾT HỌC PHẦN =================

    public List<Map<String, Object>> summaries(UUID classId) {
        classInfo(classId);

        return query("""
                SELECT ss.*
                FROM dbo.student_summaries ss
                JOIN dbo.course_registrations r
                    ON r.id = ss.registration_id
                WHERE r.course_class_id = ?
                  AND r.deleted_at IS NULL
                  AND ss.is_active = 1
                  AND ss.deleted_at IS NULL
                """, classId);
    }

    @Transactional
    public void calculate(UUID classId, boolean finalize) {
        lock();
        editable(classId);

        var students = registrations(classId);
        var components = components(classId);

        check(
                !students.isEmpty(),
                "Lớp chưa có sinh viên đăng ký."
        );

        check(
                !components.isEmpty(),
                "Lớp chưa có thành phần điểm."
        );

        var scaleList = scales().stream()
                .map(scale -> new GradeCalculator.Scale(
                        id(scale.get("id")),
                        number(scale.get("min_score"), "0"),
                        number(scale.get("max_score"), "10"),
                        scale.get("letter_grade").toString(),
                        number(scale.get("gpa_value"), "0"),
                        yes(scale.get("is_pass"))
                ))
                .toList();

        Map<String, BigDecimal> scores = new HashMap<>();

        for (var grade : grades(classId)) {
            String key = id(grade.get("registration_id"))
                    + ":" + id(grade.get("component_id"));

            check(
                    !scores.containsKey(key),
                    "Có ô điểm hiện hành bị trùng."
            );

            scores.put(
                    key,
                    grade.get("score") == null
                            ? null
                            : number(grade.get("score"), "0")
            );
        }

        for (var student : students) {
            UUID registrationId = id(
                    student.get("registration_id")
            );

            var inputs = components.stream()
                    .map(component -> new GradeCalculator.Input(
                            component.get("component_code").toString(),
                            number(
                                    component.get("weight_percentage"),
                                    "0"
                            ),
                            number(component.get("min_score"), "0"),
                            number(component.get("max_score"), "10"),
                            yes(component.get("is_required")),
                            scores.get(
                                    registrationId
                                    + ":" + id(component.get("id"))
                            )
                    ))
                    .toList();

            GradeCalculator.Result result;

            try {
                result = GradeCalculator.calculate(
                        inputs,
                        scaleList
                );
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException(
                        student.get("code")
                        + ": " + exception.getMessage()
                );
            }

            var summary = optional("""
                    SELECT id
                    FROM dbo.student_summaries
                    WHERE registration_id = ?
                      AND is_active = 1
                      AND deleted_at IS NULL
                    """, registrationId);

            if (summary == null) {
                update("""
                        INSERT dbo.student_summaries (
                            id,
                            registration_id,
                            total_score,
                            scale_id,
                            letter_grade,
                            gpa_value,
                            result,
                            is_finalized,
                            is_active,
                            created_at
                        )
                        VALUES (
                            ?, ?, ?, ?, ?, ?, ?, ?, 1, SYSDATETIME()
                        )
                        """,
                        UUID.randomUUID(),
                        registrationId,
                        result.total(),
                        result.scaleId(),
                        result.letter(),
                        result.gpa(),
                        result.result(),
                        finalize
                );
            } else {
                update("""
                        UPDATE dbo.student_summaries
                        SET total_score = ?,
                            scale_id = ?,
                            letter_grade = ?,
                            gpa_value = ?,
                            result = ?,
                            is_finalized = ?,
                            updated_at = SYSDATETIME()
                        WHERE id = ?
                        """,
                        result.total(),
                        result.scaleId(),
                        result.letter(),
                        result.gpa(),
                        result.result(),
                        finalize,
                        id(summary.get("id"))
                );
            }
        }

        if (finalize) {
            update("""
                    UPDATE g
                    SET is_locked = 1,
                        updated_at = SYSDATETIME()
                    FROM dbo.student_component_grades g
                    JOIN dbo.course_registrations r
                        ON r.id = g.registration_id
                    WHERE r.course_class_id = ?
                      AND r.deleted_at IS NULL
                      AND g.is_active = 1
                      AND g.deleted_at IS NULL
                    """, classId);
        }
    }

    @Transactional
    public void unlock(UUID classId) {
        lock();
        classInfo(classId);

        update("""
                UPDATE ss
                SET is_finalized = 0,
                    updated_at = SYSDATETIME()
                FROM dbo.student_summaries ss
                JOIN dbo.course_registrations r
                    ON r.id = ss.registration_id
                WHERE r.course_class_id = ?
                  AND ss.is_active = 1
                  AND ss.deleted_at IS NULL
                """, classId);

        update("""
                UPDATE g
                SET is_locked = 0,
                    updated_at = SYSDATETIME()
                FROM dbo.student_component_grades g
                JOIN dbo.course_registrations r
                    ON r.id = g.registration_id
                WHERE r.course_class_id = ?
                  AND g.is_active = 1
                  AND g.deleted_at IS NULL
                """, classId);
    }

    @Transactional
    public void deleteSummary(
            UUID classId,
            UUID summaryId
    ) {
        lock();
        editable(classId);

        var row = one("""
                SELECT ss.*
                FROM dbo.student_summaries ss
                JOIN dbo.course_registrations r
                    ON r.id = ss.registration_id
                WHERE ss.id = ?
                  AND r.course_class_id = ?
                  AND r.deleted_at IS NULL
                  AND ss.is_active = 1
                  AND ss.deleted_at IS NULL
                """, summaryId, classId);

        check(
                !yes(row.get("is_finalized")),
                "Phải mở điểm trước khi xóa tổng kết."
        );

        update("""
                UPDATE dbo.student_summaries
                SET is_active = 0,
                    deleted_at = SYSDATETIME(),
                    updated_at = SYSDATETIME()
                WHERE id = ?
                """, summaryId);
    }

    // ================= BẢNG ĐIỂM SINH VIÊN =================

    public List<Map<String, Object>> transcript(UUID studentId) {
        one("""
                SELECT id
                FROM dbo.students
                WHERE id = ?
                  AND deleted_at IS NULL
                """, studentId);

        return query("""
                SELECT
                    s.code AS student_code,
                    s.full_name,
                    cc.class_code,
                    c.code AS course_code,
                    c.name AS course_name,
                    c.credits,
                    sem.name AS semester_name,
                    ss.total_score,
                    ss.letter_grade,
                    ss.gpa_value,
                    ss.result
                FROM dbo.student_summaries ss
                JOIN dbo.course_registrations r
                    ON r.id = ss.registration_id
                JOIN dbo.students s
                    ON s.id = r.student_id
                JOIN dbo.courses_classes cc
                    ON cc.id = r.course_class_id
                JOIN dbo.courses c
                    ON c.id = cc.course_id
                LEFT JOIN dbo.semesters sem
                    ON sem.id = cc.semester_id
                WHERE r.student_id = ?
                  AND r.deleted_at IS NULL
                  AND ss.is_active = 1
                  AND ss.deleted_at IS NULL
                  AND ss.is_finalized = 1
                ORDER BY sem.start_date, cc.class_code
                """, studentId);
    }
}