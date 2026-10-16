package com.example.AndesStayRabbitAdmin.controller;

import com.example.AndesStayRabbitAdmin.dto.RabbitResponses;
import com.example.AndesStayRabbitAdmin.service.RabbitAdminException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import java.util.List;

@RestControllerAdvice
public class RabbitAdminExceptionHandler {
    @ExceptionHandler(RabbitAdminException.class)
    public ResponseEntity<RabbitResponses.Error> brokerFailure(RabbitAdminException failure) {
        return ResponseEntity.status(failure.status()).body(new RabbitResponses.Error(failure.code(), failure.getMessage(), List.of()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<RabbitResponses.Error> invalidBody(MethodArgumentNotValidException failure) {
        var fields = failure.getBindingResult().getFieldErrors().stream()
                .map(error -> new RabbitResponses.FieldError(error.getField(), error.getDefaultMessage())).toList();
        return ResponseEntity.badRequest().body(new RabbitResponses.Error("VALIDATION_ERROR", "Revisa los campos de la solicitud", fields));
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<RabbitResponses.Error> invalidParameters(HandlerMethodValidationException failure) {
        return ResponseEntity.badRequest().body(new RabbitResponses.Error("VALIDATION_ERROR", "Revisa los nombres y la paginación", List.of()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<RabbitResponses.Error> invalidJson(HttpMessageNotReadableException failure) {
        return ResponseEntity.badRequest().body(new RabbitResponses.Error("INVALID_JSON", "El JSON contiene campos o tipos inválidos", List.of()));
    }
}
