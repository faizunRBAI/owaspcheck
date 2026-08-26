package com.enterprise.idp.exception;

/** Thrown when creating a resource that violates a uniqueness constraint. */
public class DuplicateResourceException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public DuplicateResourceException(String message) {
        super(message);
    }
}
