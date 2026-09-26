package com.ecommerce.oms.common;

import org.springframework.http.HttpStatus;

/** Business error that carries its HTTP status; GlobalExceptionHandler renders it as ProblemDetail. */
public class ApiException extends RuntimeException {

    private final HttpStatus status;

    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
