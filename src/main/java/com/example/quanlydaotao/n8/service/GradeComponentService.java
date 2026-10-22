package com.example.quanlydaotao.n8.service;

import com.example.quanlydaotao.n8.dto.request.GradeRequests.Component;
import com.example.quanlydaotao.n8.repository.GradeComponentRepository;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;

@Service
public class GradeComponentService {

    private final GradeComponentRepository repository;
    private final GradeService workflow;

    public GradeComponentService(
            GradeComponentRepository repository,
            GradeService workflow
    ) {
        this.repository = repository;
        this.workflow = workflow;
    }

    public List<Map<String, Object>> findAll(UUID classId) {
        return repository.findAll(classId);
    }

    public Map<String, Object> findById(UUID classId, UUID id) {
        return repository.findById(classId, id);
    }

    public UUID save(UUID classId, Component request) {
        return workflow.saveComponent(classId, request);
    }

    public void update(UUID classId, UUID id, Component request) {
        workflow.saveComponent(
                classId,
                new Component(
                        id,
                        request.component_code(),
                        request.component_name(),
                        request.weight_percentage(),
                        request.min_score(),
                        request.max_score(),
                        request.is_required(),
                        request.input_order()
                )
        );
    }

    public void delete(UUID classId, UUID id) {
        workflow.deleteComponent(classId, id);
    }
}