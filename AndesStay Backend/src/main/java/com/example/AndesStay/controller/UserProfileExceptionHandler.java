package com.example.AndesStay.controller;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice(assignableTypes = UserProfileController.class)
public class UserProfileExceptionHandler {
    @ExceptionHandler(ResponseStatusException.class)
    public ProblemDetail validation(ResponseStatusException error) {
        return ProblemDetail.forStatusAndDetail(error.getStatusCode(), error.getReason());
    }
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail invalidBody(HttpMessageNotReadableException error) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Indica los datos del perfil con un formato válido.");
    }
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail duplicate(DataIntegrityViolationException error) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "No se pudo crear el perfil. Actualiza la página para comprobar si ya está registrado.");
    }
}
