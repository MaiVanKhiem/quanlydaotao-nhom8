package com.example.quanlydaotao.n8.service;

import com.example.quanlydaotao.n8.dto.request.GradeRequests.Grade;
import com.example.quanlydaotao.n8.dto.request.GradeRequests.GradeBatch;
import com.example.quanlydaotao.n8.repository.StudentComponentGradeRepository;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;

@Service
public class StudentComponentGradeService {

    private final StudentComponentGradeRepository repository;
    private final GradeService workflow;

    public StudentComponentGradeService(
            StudentComponentGradeRepository repository,
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

    public void save(UUID classId, GradeBatch request) {
        workflow.saveGrades(classId, request);
    }

    public void update(UUID classId, UUID id, Grade request) {
        workflow.updateGrade(classId, id, request);
    }

    public void delete(UUID classId, UUID id) {
        workflow.deleteGrade(classId, id);
    }
}