package com.example.AndesStay.service;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class Pagination {
    private Pagination() {}

    public static PageRequest request(int page, int size, Sort sort) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La página debe ser 0 o mayor y el tamaño debe estar entre 1 y 100");
        }
        return PageRequest.of(page, size, sort);
    }
}
