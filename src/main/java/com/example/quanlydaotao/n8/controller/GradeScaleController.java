package com.example.quanlydaotao.n8.controller;

import com.example.quanlydaotao.n8.dto.request.GradeRequests.Scale;
import com.example.quanlydaotao.n8.dto.response.MessageResponse;
import com.example.quanlydaotao.n8.service.GradeScaleService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/n8/scales")
@Tag(name = "05. Thang điểm quy đổi")
public class GradeScaleController {

    private final GradeScaleService service;

    public GradeScaleController(GradeScaleService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Xem danh sách thang điểm")
    public List<Map<String, Object>> list() {
        return service.findAll();
    }

    @GetMapping("/{scaleId}")
    @Operation(summary = "Xem chi tiết thang điểm")
    public Map<String, Object> detail(
            @PathVariable("scaleId") UUID id
    ) {
        return service.findById(id);
    }

    @PostMapping
    @Operation(summary = "Thêm thang điểm; id=null khi tạo mới")
    public Map<String, Object> save(
            @RequestBody Scale request
    ) {
        return Map.of(
                "id", service.save(request),
                "message", "Đã lưu thang điểm."
        );
    }

    @PutMapping("/{scaleId}")
    @Operation(summary = "Sửa thang điểm")
    public MessageResponse update(
            @PathVariable("scaleId") UUID id,
            @RequestBody Scale request
    ) {
        service.update(id, request);
        return new MessageResponse("Đã sửa thang điểm.");
    }

    @DeleteMapping("/{scaleId}")
    @Operation(summary = "Xóa mềm thang điểm")
    public MessageResponse delete(
            @PathVariable("scaleId") UUID id
    ) {
        service.delete(id);
        return new MessageResponse("Đã xóa thang điểm.");
    }
}
