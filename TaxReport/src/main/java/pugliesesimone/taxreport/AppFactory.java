package pugliesesimone.taxreport;

import pugliesesimone.taxreport.metadata.MariaDbMetadata;
import pugliesesimone.taxreport.metadata.MetadataInterface;
import pugliesesimone.taxreport.service.TaxReportService;
import pugliesesimone.taxreport.storage.FileSystemStorage;
import pugliesesimone.taxreport.storage.SmbStorage;
import pugliesesimone.taxreport.storage.StorageInterface;

public class AppFactory {

    /**
     * Factory per WINDOWS (o Linux/Mac desktop).
     *
     * @param dbHost Host del Raspberry (es. "192.168.1.50")
     * @param dbPort Porta MariaDB (es. 3306)
     * @param dbName Nome DB (es. "taxreport")
     * @param dbUser Utente DB
     * @param dbPass Password DB
     * @param networkPath Path della cartella condivisa montata o UNC (es. "Z:\" o "\\RASPBERRY\TaxData")
     */
    public static TaxReportService buildWindowsService(
            String dbHost, int dbPort, String dbName, String dbUser, String dbPass,
            String networkPath) {

        StorageInterface storage = new FileSystemStorage(networkPath);
        MetadataInterface metadata = new MariaDbMetadata(dbHost, dbPort, dbName, dbUser, dbPass);
        return new TaxReportService(storage, metadata);
    }

    /**
     * Factory per ANDROID.
     *
     * @param dbHost Host del Raspberry (es. "192.168.1.50")
     * @param dbPort Porta MariaDB (es. 3306)
     * @param dbName Nome DB (es. "taxreport")
     * @param dbUser Utente DB
     * @param dbPass Password DB
     * @param smbHost Host SMB (spesso uguale a dbHost)
     * @param smbShare Nome dello share SMB (es. "TaxData")
     * @param smbUser Utente SMB (es. "pi")
     * @param smbPass Password SMB
     */
    public static TaxReportService buildAndroidService(
            String dbHost, int dbPort, String dbName, String dbUser, String dbPass,
            String smbHost, String smbShare, String smbUser, String smbPass) {

        StorageInterface storage = new SmbStorage(smbHost, smbShare, smbUser, smbPass);
        MetadataInterface metadata = new MariaDbMetadata(dbHost, dbPort, dbName, dbUser, dbPass);

        return new TaxReportService(storage, metadata);
    }
}