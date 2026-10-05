package com.example.AndesStay.controller;

import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice(assignableTypes = {ReservationController.class, UnitController.class, ReportController.class})
public class ReservationExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public ProblemDetail handleValidation(ResponseStatusException exception) {
        return ProblemDetail.forStatusAndDetail(exception.getStatusCode(), exception.getReason());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleInvalidBody(HttpMessageNotReadableException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "El cuerpo de la solicitud o el formato de las fechas no es válido");
    }

    @ExceptionHandler(PessimisticLockingFailureException.class)
    public ProblemDetail handleConcurrentOperation(PessimisticLockingFailureException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "Otra operación está modificando la reserva o la unidad; vuelve a intentarlo");
    }
}
