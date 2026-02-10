package pugliesesimone.taxreport;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pugliesesimone.taxreport.exception.ConfigurationException;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class AppFactoryTest {

    @TempDir
    Path tempDir;

    @Test
    void buildWindowsService_ShouldThrowIfDbUnreachable() {
        assertThrows(ConfigurationException.class, () -> AppFactory.buildWindowsService(
                "localhost", 3306, "fakeDB", "user", "pass",
                tempDir.toString()
        ));
    }
}