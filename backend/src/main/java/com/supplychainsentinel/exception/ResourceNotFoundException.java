package com.supplychainsentinel.exception;

/**
 * Thrown when a requested resource (application, user, etc.) does not exist.
 * Handled by {@link GlobalExceptionHandler} to return HTTP 404.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
