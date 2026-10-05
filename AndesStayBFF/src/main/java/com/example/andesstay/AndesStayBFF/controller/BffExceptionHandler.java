package com.example.andesstay.AndesStayBFF.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

@RestControllerAdvice(assignableTypes = BffController.class)
public class BffExceptionHandler {

    @ExceptionHandler(RestClientResponseException.class)
    public ResponseEntity<?> handleBackendResponse(RestClientResponseException exception) {
        if (exception.getStatusCode().is4xxClientError()) {
            MediaType contentType = exception.getResponseHeaders() == null
                    ? null : exception.getResponseHeaders().getContentType();
            return ResponseEntity.status(exception.getStatusCode())
                    .contentType(contentType == null ? MediaType.APPLICATION_JSON : contentType)
                    .body(exception.getResponseBodyAsString());
        }
        return unavailable();
    }

    @ExceptionHandler(ResourceAccessException.class)
    public ResponseEntity<ProblemDetail> handleBackendUnavailable(ResourceAccessException exception) {
        return unavailable();
    }

    private ResponseEntity<ProblemDetail> unavailable() {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(
                ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY,
                        "No se pudo completar la solicitud; vuelve a intentarlo"));
    }
}
