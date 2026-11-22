package org.example.backtrackweb.exception;

/**
 * The requested record does not exist.
 */
public class NotFoundException extends BusinessException {

    public NotFoundException(String message) {
        super(message);
    }
}
