package com.example.quanlydaotao.n8.controller;

import com.example.quanlydaotao.n8.dto.response.MessageResponse;
import com.example.quanlydaotao.n8.service.StudentSummaryService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/n8/classes/{classId}")
@Tag(name = "04. Tổng kết học phần")
public class StudentSummaryController {

    private final StudentSummaryService service;

    public StudentSummaryController(StudentSummaryService service) {
        this.service = service;
    }

    @GetMapping("/summaries")
    @Operation(summary = "Xem danh sách tổng kết")
    public List<Map<String, Object>> list(
            @PathVariable("classId") UUID classId
    ) {
        return service.findAll(classId);
    }

    @GetMapping("/summaries/{summaryId}")
    @Operation(summary = "Xem chi tiết tổng kết")
    public Map<String, Object> detail(
            @PathVariable("classId") UUID classId,
            @PathVariable("summaryId") UUID id
    ) {
        return service.findById(classId, id);
    }

    @PostMapping({"/summaries", "/calculate"})
    @Operation(summary = "Tính và tạo hoặc lưu tổng kết toàn lớp")
    public MessageResponse calculate(
            @PathVariable("classId") UUID classId
    ) {
        service.calculate(classId);
        return new MessageResponse(
                "Đã tính và lưu tổng kết toàn lớp."
        );
    }

    @PutMapping("/summaries")
    @Operation(summary = "Tính lại và cập nhật tổng kết chưa chốt")
    public MessageResponse update(
            @PathVariable("classId") UUID classId
    ) {
        service.calculate(classId);
        return new MessageResponse(
                "Đã tính lại tổng kết toàn lớp."
        );
    }

    @DeleteMapping("/summaries/{summaryId}")
    @Operation(summary = "Xóa mềm tổng kết chưa chốt")
    public MessageResponse delete(
            @PathVariable("classId") UUID classId,
            @PathVariable("summaryId") UUID id
    ) {
        service.delete(classId, id);
        return new MessageResponse("Đã xóa tổng kết chưa chốt.");
    }

    @PostMapping("/finalize")
    @Operation(summary = "Tính lại, chốt tổng kết và khóa điểm")
    public MessageResponse finalizeGrades(
            @PathVariable("classId") UUID classId
    ) {
        service.finalizeGrades(classId);
        return new MessageResponse("Đã chốt điểm toàn lớp.");
    }

    @PostMapping("/unlock")
    @Operation(summary = "Mở tổng kết và các ô điểm để sửa")
    public MessageResponse unlock(
            @PathVariable("classId") UUID classId
    ) {
        service.unlock(classId);
        return new MessageResponse("Đã mở điểm toàn lớp.");
    }
}