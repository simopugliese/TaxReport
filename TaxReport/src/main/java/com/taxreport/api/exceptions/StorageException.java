package com.taxreport.api.exceptions;

public class StorageException extends TaxReportException {
    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
