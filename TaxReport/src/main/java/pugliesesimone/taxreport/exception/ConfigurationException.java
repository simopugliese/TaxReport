package pugliesesimone.taxreport.exception;

public class ConfigurationException extends TaxReportException {
    public ConfigurationException(String message) {
        super(message);
    }

    public ConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
