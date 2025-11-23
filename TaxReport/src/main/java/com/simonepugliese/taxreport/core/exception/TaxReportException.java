package com.simonepugliese.taxreport.core.exception;

public abstract class TaxReportException extends RuntimeException {
    protected TaxReportException(String message) {
        super(message);
    }

    protected TaxReportException(String message, Throwable cause) {
        super(message, cause);
    }
}
