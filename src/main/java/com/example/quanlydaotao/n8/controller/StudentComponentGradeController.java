package com.example.quanlydaotao.n8.controller;

import com.example.quanlydaotao.n8.dto.request.GradeRequests.Grade;
import com.example.quanlydaotao.n8.dto.request.GradeRequests.GradeBatch;
import com.example.quanlydaotao.n8.dto.response.MessageResponse;
import com.example.quanlydaotao.n8.service.StudentComponentGradeService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/n8/classes/{classId}/grades")
@Tag(name = "03. Điểm thành phần sinh viên")
public class StudentComponentGradeController {

    private final StudentComponentGradeService service;

    public StudentComponentGradeController(
            StudentComponentGradeService service
    ) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Xem danh sách điểm thành phần")
    public List<Map<String, Object>> list(
            @PathVariable("classId") UUID classId
    ) {
        return service.findAll(classId);
    }

    @GetMapping("/{gradeId}")
    @Operation(summary = "Xem chi tiết một ô điểm")
    public Map<String, Object> detail(
            @PathVariable("classId") UUID classId,
            @PathVariable("gradeId") UUID id
    ) {
        return service.findById(classId, id);
    }

    @PostMapping
    @Operation(summary = "Lưu nhiều ô điểm; score=null để xóa điểm")
    public MessageResponse save(
            @PathVariable("classId") UUID classId,
            @RequestBody GradeBatch request
    ) {
        service.save(classId, request);
        return new MessageResponse(
                "Đã lưu điểm. Hãy tính lại tổng kết."
        );
    }

    @PutMapping("/{gradeId}")
    @Operation(summary = "Sửa một ô điểm")
    public MessageResponse update(
            @PathVariable("classId") UUID classId,
            @PathVariable("gradeId") UUID id,
            @RequestBody Grade request
    ) {
        service.update(classId, id, request);
        return new MessageResponse(
                "Đã sửa điểm. Hãy tính lại tổng kết."
        );
    }

    @DeleteMapping("/{gradeId}")
    @Operation(summary = "Xóa mềm một ô điểm")
    public MessageResponse delete(
            @PathVariable("classId") UUID classId,
            @PathVariable("gradeId") UUID id
    ) {
        service.delete(classId, id);
        return new MessageResponse(
                "Đã xóa điểm. Hãy tính lại tổng kết."
        );
    }
}