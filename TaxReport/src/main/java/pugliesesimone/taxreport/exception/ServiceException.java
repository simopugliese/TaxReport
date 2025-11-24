package pugliesesimone.taxreport.exception;

public class ServiceException extends TaxReportException {
    public ServiceException(String message) {
        super(message);
    }
    public ServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
