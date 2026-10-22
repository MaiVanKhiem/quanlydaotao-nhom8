package com.example.quanlydaotao.n8.exception;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log =
            LoggerFactory.getLogger(ApiExceptionHandler.class);

    private ResponseEntity<Map<String, String>> error(
            int status,
            String message
    ) {
        return ResponseEntity.status(status)
                .body(Map.of("message", message));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> status(
            ResponseStatusException e
    ) {
        return error(
                e.getStatusCode().value(),
                e.getReason() == null
                        ? "Yêu cầu không hợp lệ."
                        : e.getReason()
        );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> validation(
            IllegalArgumentException e
    ) {
        return error(
                400,
                e.getMessage() == null
                        ? "Dữ liệu không hợp lệ."
                        : e.getMessage()
        );
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<Map<String, String>> input(
            Exception e
    ) {
        return error(
                400,
                "JSON hoặc kiểu dữ liệu không hợp lệ. "
                        + "ID phải đúng định dạng UUID."
        );
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> conflict(
            DataIntegrityViolationException e
    ) {
        log.error("SQL constraint error", e);

        return error(
                409,
                "Dữ liệu bị trùng hoặc vi phạm ràng buộc SQL. "
                        + "Xem chi tiết trong Terminal Java."
        );
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Map<String, String>> sql(
            DataAccessException e
    ) {
        log.error("SQL error", e);

        return error(
                500,
                "Không truy vấn được SQL Server. "
                        + "Xem thông báo SQLServerException "
                        + "trong Terminal Java."
        );
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, String>> missing(
            NoResourceFoundException e
    ) {
        return error(404, "Không tìm thấy đường dẫn.");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> unexpected(
            Exception e
    ) {
        log.error("Application error", e);

        return error(
                500,
                "Có lỗi xử lý. Xem chi tiết trong Terminal Java."
        );
    }
}