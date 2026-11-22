package org.example.backtrackweb.exception;

/**
 * A business rule was broken (not enough stock, duplicate SKU, ...).
 * The message is safe to show to the user.
 */
public class BusinessException extends RuntimeException {

    public BusinessException(String message) {
        super(message);
    }
}
