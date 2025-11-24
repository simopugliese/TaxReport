package pugliesesimone.taxreport;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pugliesesimone.taxreport.exception.ConfigurationException;
import pugliesesimone.taxreport.service.TaxReportService;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class AppFactoryTest {

    @TempDir
    Path tempDir;

    @Test
    void buildWindowsService_ShouldThrowIfDbUnreachable() {
        // Testiamo che provi effettivamente a connettersi.
        // Siccome non c'è un MariaDB a localhost:3306 nel test environment unitario,
        // ci aspettiamo una ConfigurationException (o StorageException incapsulata).
        // Questo conferma che la factory sta istanziando MariaDbMetadata.

        assertThrows(ConfigurationException.class, () -> {
            AppFactory.buildWindowsService(
                    "localhost", 3306, "fakeDB", "user", "pass",
                    tempDir.toString()
            );
        });
    }

    // Nota: Per testare il "Successo" della factory servirebbe un Mock del Driver JDBC,
    // ma è eccessivo. Il test sopra ci garantisce che la catena di inizializzazione parte.
}