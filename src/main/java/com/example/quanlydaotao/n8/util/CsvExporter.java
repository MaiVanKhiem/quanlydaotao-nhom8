package com.example.quanlydaotao.n8.util;

import java.util.List;
import java.util.Map;

public final class CsvExporter {

    private CsvExporter() {
    }

    private static String cell(Object value) {
        String text = value == null ? "" : value.toString();
        String trimmed = text.stripLeading();

        // Tránh Excel hiểu nội dung văn bản thành công thức.
        if (!trimmed.isEmpty()
                && "=+@-".indexOf(trimmed.charAt(0)) >= 0) {
            text = "'" + text;
        }

        return "\"" + text.replace("\"", "\"\"") + "\"";
    }

    public static String transcript(
            List<Map<String, Object>> rows
    ) {
        List<String> fields = List.of(
                "student_code",
                "full_name",
                "class_code",
                "course_code",
                "course_name",
                "credits",
                "semester_name",
                "total_score",
                "letter_grade",
                "gpa_value",
                "result"
        );

        // BOM giúp Excel đọc đúng tiếng Việt.
        StringBuilder csv = new StringBuilder("\uFEFF");

        csv.append(
                "Mã sinh viên,Họ tên,Mã lớp,Mã học phần,"
                        + "Tên học phần,Tín chỉ,Học kỳ,"
                        + "Điểm hệ 10,Điểm chữ,Điểm hệ 4,Kết quả\r\n"
        );

        for (Map<String, Object> row : rows) {
            csv.append(
                    String.join(
                            ",",
                            fields.stream()
                                    .map(field -> cell(row.get(field)))
                                    .toList()
                    )
            ).append("\r\n");
        }

        return csv.toString();
    }
}