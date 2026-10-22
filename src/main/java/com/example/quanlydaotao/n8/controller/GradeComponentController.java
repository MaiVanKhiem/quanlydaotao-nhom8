package com.example.quanlydaotao.n8.controller;

import com.example.quanlydaotao.n8.dto.request.GradeRequests.Component;
import com.example.quanlydaotao.n8.dto.response.MessageResponse;
import com.example.quanlydaotao.n8.service.GradeComponentService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/n8/classes/{classId}/components")
@Tag(name = "02. Thành phần điểm")
public class GradeComponentController {

    private final GradeComponentService service;

    public GradeComponentController(GradeComponentService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Xem danh sách thành phần điểm")
    public List<Map<String, Object>> list(
            @PathVariable("classId") UUID classId
    ) {
        return service.findAll(classId);
    }

    @GetMapping("/{componentId}")
    @Operation(summary = "Xem chi tiết thành phần điểm")
    public Map<String, Object> detail(
            @PathVariable("classId") UUID classId,
            @PathVariable("componentId") UUID id
    ) {
        return service.findById(classId, id);
    }

    @PostMapping
    @Operation(summary = "Thêm thành phần; id=null khi tạo mới")
    public Map<String, Object> save(
            @PathVariable("classId") UUID classId,
            @RequestBody Component request
    ) {
        return Map.of(
                "id", service.save(classId, request),
                "message", "Đã lưu thành phần điểm."
        );
    }

    @PutMapping("/{componentId}")
    @Operation(summary = "Sửa thành phần điểm")
    public MessageResponse update(
            @PathVariable("classId") UUID classId,
            @PathVariable("componentId") UUID id,
            @RequestBody Component request
    ) {
        service.update(classId, id, request);
        return new MessageResponse("Đã sửa thành phần điểm.");
    }

    @DeleteMapping("/{componentId}")
    @Operation(summary = "Xóa mềm thành phần và các điểm liên quan")
    public MessageResponse delete(
            @PathVariable("classId") UUID classId,
            @PathVariable("componentId") UUID id
    ) {
        service.delete(classId, id);
        return new MessageResponse(
                "Đã xóa thành phần và các điểm liên quan."
        );
    }
}