package com.example.quanlydaotao.n8.controller;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.quanlydaotao.n8.dto.response.MessageResponse;
import com.example.quanlydaotao.n8.service.GradeComponentService;
import com.example.quanlydaotao.n8.service.GradeScaleService;
import com.example.quanlydaotao.n8.service.GradeService;
import com.example.quanlydaotao.n8.service.StudentComponentGradeService;
import com.example.quanlydaotao.n8.service.StudentSummaryService;
import com.example.quanlydaotao.n8.util.CsvExporter;

import io.swagger.v3.oas.annotations.Operation;

@RestController
@RequestMapping("/api/n8")
public class CompactGradeController {

    public enum View {
        CLASSES,
        STUDENTS,
        BOARD
    }

    public enum Action {
        CALCULATE,
        FINALIZE,
        UNLOCK
    }

    public enum Format {
        JSON,
        CSV
    }

    private final GradeService workflow;
    private final GradeComponentService components;
    private final StudentComponentGradeService grades;
    private final StudentSummaryService summaries;
    private final GradeScaleService scales;

    public CompactGradeController(
            GradeService workflow,
            GradeComponentService components,
            StudentComponentGradeService grades,
            StudentSummaryService summaries,
            GradeScaleService scales
    ) {
        this.workflow = workflow;
        this.components = components;
        this.grades = grades;
        this.summaries = summaries;
        this.scales = scales;
    }

    @GetMapping("/manage")
    @Operation(
            tags = "01. Lớp học phần và sinh viên",
            summary = "Xem lớp, tìm sinh viên hoặc xem bảng điểm lớp",
            description = """
                    Chọn view:
                    - CLASSES: xem danh sách lớp.
                    - STUDENTS: tìm sinh viên bằng keyword.
                    - BOARD: nhập classId để xem bảng điểm lớp.
                    """
    )
    public Object manage(
            @RequestParam(name = "view", defaultValue = "CLASSES")
            View view,

            @RequestParam(name = "keyword", defaultValue = "")
            String keyword,

            @RequestParam(name = "classId", required = false)
            UUID classId
    ) {
        return switch (view) {
            case CLASSES -> workflow.classes();

            case STUDENTS -> workflow.students(keyword);

            case BOARD -> {
                if (classId == null) {
                    throw new IllegalArgumentException(
                            "Chọn BOARD thì phải nhập classId."
                    );
                }

                yield workflow.board(classId);
            }
        };
    }

    @GetMapping("/classes/{classId}/components/view")
    @Operation(
            tags = "02. Thành phần điểm",
            summary = "Xem danh sách hoặc chi tiết thành phần",
            description = """
                    Để trống componentId: xem danh sách.
                    Nhập componentId: xem chi tiết một thành phần.
                    """
    )
    public Object components(
            @PathVariable("classId") UUID classId,

            @RequestParam(name = "componentId", required = false)
            UUID componentId
    ) {
        return componentId == null
                ? components.findAll(classId)
                : components.findById(classId, componentId);
    }

    @GetMapping("/classes/{classId}/grades/view")
    @Operation(
            tags = "03. Điểm thành phần sinh viên",
            summary = "Xem danh sách hoặc chi tiết điểm",
            description = """
                    Để trống gradeId: xem danh sách.
                    Nhập gradeId: xem chi tiết một ô điểm.
                    """
    )
    public Object grades(
            @PathVariable("classId") UUID classId,

            @RequestParam(name = "gradeId", required = false)
            UUID gradeId
    ) {
        return gradeId == null
                ? grades.findAll(classId)
                : grades.findById(classId, gradeId);
    }

    @GetMapping("/classes/{classId}/summaries/view")
    @Operation(
            tags = "04. Tổng kết học phần",
            summary = "Xem danh sách hoặc chi tiết tổng kết",
            description = """
                    Để trống summaryId: xem danh sách.
                    Nhập summaryId: xem chi tiết một tổng kết.
                    """
    )
    public Object summaries(
            @PathVariable("classId") UUID classId,

            @RequestParam(name = "summaryId", required = false)
            UUID summaryId
    ) {
        return summaryId == null
                ? summaries.findAll(classId)
                : summaries.findById(classId, summaryId);
    }

    @PostMapping("/classes/{classId}/summaries/action")
    @Operation(
            tags = "04. Tổng kết học phần",
            summary = "Tính, chốt hoặc mở điểm toàn lớp",
            description = """
                    Chọn action:
                    - CALCULATE: tính tổng kết.
                    - FINALIZE: tính lại và chốt điểm.
                    - UNLOCK: mở điểm để chỉnh sửa.
                    """
    )
    public MessageResponse summaryAction(
            @PathVariable("classId") UUID classId,

            @RequestParam(name = "action", defaultValue = "CALCULATE")
            Action action
    ) {
        return switch (action) {
            case CALCULATE -> {
                summaries.calculate(classId);

                yield new MessageResponse(
                        "Đã tính tổng kết toàn lớp."
                );
            }

            case FINALIZE -> {
                summaries.finalizeGrades(classId);

                yield new MessageResponse(
                        "Đã chốt điểm toàn lớp."
                );
            }

            case UNLOCK -> {
                summaries.unlock(classId);

                yield new MessageResponse(
                        "Đã mở điểm toàn lớp."
                );
            }
        };
    }

    @GetMapping("/scales/view")
    @Operation(
            tags = "05. Thang điểm quy đổi",
            summary = "Xem danh sách hoặc chi tiết thang điểm",
            description = """
                    Để trống scaleId: xem danh sách.
                    Nhập scaleId: xem chi tiết một mức quy đổi.
                    """
    )
    public Object scales(
            @RequestParam(name = "scaleId", required = false)
            UUID scaleId
    ) {
        return scaleId == null
                ? scales.findAll()
                : scales.findById(scaleId);
    }

    @GetMapping("/students/{studentId}/transcript/view")
    @Operation(
            tags = "06. Bảng điểm sinh viên",
            summary = "Xem bảng điểm hoặc tải CSV",
            description = """
                    Chọn format:
                    - JSON: xem bảng điểm.
                    - CSV: xuất bảng điểm dạng CSV.
                    """
    )
    public ResponseEntity<?> transcript(
            @PathVariable("studentId") UUID studentId,

            @RequestParam(name = "format", defaultValue = "JSON")
            Format format
    ) {
        List<Map<String, Object>> rows =
                workflow.transcript(studentId);

        if (format == Format.CSV) {
            return ResponseEntity.ok()
                    .header(
                            "Content-Disposition",
                            "attachment; filename=bang-diem.csv"
                    )
                    .contentType(
                            MediaType.parseMediaType(
                                    "text/csv;charset=UTF-8"
                            )
                    )
                    .body(CsvExporter.transcript(rows));
        }

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(rows);
    }
}