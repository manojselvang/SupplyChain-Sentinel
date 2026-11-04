package com.supplychainsentinel.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown for authentication-related failures (bad credentials, disabled, locked, etc.).
 * Carries an HTTP status so the global handler can return the right code.
 */
public class AuthException extends RuntimeException {

    private final HttpStatus status;

    public AuthException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
