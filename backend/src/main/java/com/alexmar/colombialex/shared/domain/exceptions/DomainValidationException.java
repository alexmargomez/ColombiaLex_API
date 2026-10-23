package com.alexmar.colombialex.shared.domain.exceptions;

public class DomainValidationException extends RuntimeException {
    private final String code;

    public DomainValidationException(String message) {
        super(message);
        this.code = "DOMAIN_VALIDATION_ERROR";
    }

    public DomainValidationException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
