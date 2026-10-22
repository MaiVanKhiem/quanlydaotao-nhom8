package com.example.quanlydaotao.n8.service;

import com.example.quanlydaotao.n8.dto.request.GradeRequests.Scale;
import com.example.quanlydaotao.n8.repository.GradeScaleRepository;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;

@Service
public class GradeScaleService {

    private final GradeScaleRepository repository;
    private final GradeService workflow;

    public GradeScaleService(
            GradeScaleRepository repository,
            GradeService workflow
    ) {
        this.repository = repository;
        this.workflow = workflow;
    }

    public List<Map<String, Object>> findAll() {
        return repository.findAll();
    }

    public Map<String, Object> findById(UUID id) {
        return repository.findById(id);
    }

    public UUID save(Scale request) {
        return workflow.saveScale(request);
    }

    public void update(UUID id, Scale request) {
        workflow.saveScale(
                new Scale(
                        id,
                        request.scale_code(),
                        request.min_score(),
                        request.max_score(),
                        request.letter_grade(),
                        request.gpa_value(),
                        request.is_pass()
                )
        );
    }

    public void delete(UUID id) {
        workflow.deleteScale(id);
    }
}