package com.enterprise.idp.exception;

/** Thrown when a requested catalog resource does not exist. */
public class ResourceNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ResourceNotFoundException(String resource, Long id) {
        super(resource + " with id " + id + " was not found");
    }

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
