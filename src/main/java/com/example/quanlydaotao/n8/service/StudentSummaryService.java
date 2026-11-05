package com.example.quanlydaotao.n8.service;

import com.example.quanlydaotao.n8.repository.StudentSummaryRepository;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;

@Service
public class StudentSummaryService {

    private final StudentSummaryRepository repository;
    private final GradeService workflow;

    public StudentSummaryService(
            StudentSummaryRepository repository,
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

    public void calculate(UUID classId) {
        workflow.calculate(classId, false);
    }

    public void finalizeGrades(UUID classId) {
        workflow.calculate(classId, true);
    }

    public void unlock(UUID classId) {
        workflow.unlock(classId);
    }

    public void delete(UUID classId, UUID id) {
        workflow.deleteSummary(classId, id);
    }
}