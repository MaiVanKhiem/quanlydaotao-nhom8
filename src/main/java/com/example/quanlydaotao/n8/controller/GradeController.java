package com.example.quanlydaotao.n8.controller;

import com.example.quanlydaotao.n8.dto.request.GradeRequests.Enrollment;
import com.example.quanlydaotao.n8.dto.response.MessageResponse;
import com.example.quanlydaotao.n8.service.GradeService;
import com.example.quanlydaotao.n8.util.CsvExporter;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletResponse;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/n8")
public class GradeController {

    private static final String CLASSES =
            "01. Lớp học phần và sinh viên";

    private static final String TRANSCRIPT =
            "06. Bảng điểm sinh viên";

    private final GradeService service;

    public GradeController(GradeService service) {
        this.service = service;
    }

    @GetMapping("/classes")
    @Operation(tags = CLASSES, summary = "Xem danh sách lớp học phần")
    public List<Map<String, Object>> classes() {
        return service.classes();
    }

    @GetMapping("/students")
    @Operation(tags = CLASSES, summary = "Tìm sinh viên theo mã hoặc tên")
    public List<Map<String, Object>> students(
            @RequestParam(
                    name = "keyword",
                    defaultValue = ""
            ) String keyword
    ) {
        return service.students(keyword);
    }

    @GetMapping("/classes/{classId}/board")
    @Operation(tags = CLASSES, summary = "Xem bảng điểm đầy đủ của lớp")
    public Map<String, Object> board(
            @PathVariable("classId") UUID classId
    ) {
        return service.board(classId);
    }

    @PostMapping("/classes/{classId}/students")
    @Operation(tags = CLASSES, summary = "Thêm sinh viên vào lớp")
    public MessageResponse enroll(
            @PathVariable("classId") UUID classId,
            @RequestBody Enrollment request
    ) {
        service.enroll(classId, request);
        return new MessageResponse("Đã thêm sinh viên vào lớp.");
    }

    @DeleteMapping("/classes/{classId}/students/{registrationId}")
    @Operation(tags = CLASSES, summary = "Bỏ sinh viên chưa có điểm khỏi lớp")
    public MessageResponse removeStudent(
            @PathVariable("classId") UUID classId,
            @PathVariable("registrationId") UUID id
    ) {
        service.removeRegistration(classId, id);
        return new MessageResponse("Đã bỏ sinh viên khỏi lớp.");
    }

    @GetMapping("/students/{studentId}/transcript")
    @Operation(tags = TRANSCRIPT, summary = "Xem bảng điểm học phần đã chốt")
    public List<Map<String, Object>> transcript(
            @PathVariable("studentId") UUID studentId
    ) {
        return service.transcript(studentId);
    }

    @GetMapping(
            value = "/students/{studentId}/transcript/export",
            produces = "text/csv;charset=UTF-8"
    )
    @Operation(tags = TRANSCRIPT, summary = "Xuất bảng điểm ra CSV")
    public String exportTranscript(
            @PathVariable("studentId") UUID studentId,
            HttpServletResponse response
    ) {
        var rows = service.transcript(studentId);

        response.setHeader(
                "Content-Disposition",
                "attachment; filename=bang-diem.csv"
        );

        return CsvExporter.transcript(rows);
    }
}