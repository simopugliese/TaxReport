package com.taxreport.api.exceptions;

public class ValidationException extends TaxReportException {
    public ValidationException(String message) {
        super(message);
    }
}
