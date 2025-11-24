package com.taxreport.api.exceptions;

public abstract class TaxReportException extends RuntimeException {
    protected TaxReportException(String message) {
        super(message);
    }

    protected TaxReportException(String message, Throwable cause) {
        super(message, cause);
    }
}
