package com.simonepugliese.taxreport.core.impl;

import com.simonepugliese.taxreport.core.api.ExpenseService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.lang.reflect.Field;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class TaxReportFactoryTest {

    @TempDir
    Path tempPath;

    @Test
    void testCreateLocalService() {
        ExpenseService service = TaxReportFactory.createLocalService(tempPath.toString());
        assertNotNull(service);
        // Verifica "White Box": controlliamo che dentro ci sia il JsonMetadataRepository
        assertRepositoryType(service, JsonMetadataRepository.class);
    }

    @Test
    void testCreateSqlService() {
        ExpenseService service = TaxReportFactory.createSqlService(tempPath.toString());
        assertNotNull(service);

        // Verifica che abbia creato il file DB
        assertTrue(tempPath.resolve("taxreport.db").toFile().exists());

        // Verifica "White Box": controlliamo che dentro ci sia il SqlMetadataRepository
        assertRepositoryType(service, SqlMetadataRepository.class);
    }

    @Test
    void testCreateVolatileService() {
        ExpenseService service = TaxReportFactory.createVolatileService(tempPath.toString());
        assertNotNull(service);
        assertRepositoryType(service, InMemoryMetadataRepository.class);
    }

    // Helper method per ispezionare i campi privati del Service (Reflection per i test)
    private void assertRepositoryType(ExpenseService service, Class<?> expectedRepoClass) {
        try {
            // ExpenseServiceImpl ha un campo 'repository'
            Field repoField = service.getClass().getDeclaredField("repository");
            repoField.setAccessible(true);
            Object actualRepo = repoField.get(service);

            assertTrue(expectedRepoClass.isInstance(actualRepo),
                    "Il service dovrebbe usare " + expectedRepoClass.getSimpleName() +
                            " ma usa " + actualRepo.getClass().getSimpleName());

        } catch (NoSuchFieldException | IllegalAccessException e) {
            fail("Impossibile ispezionare il service via reflection: " + e.getMessage());
        }
    }
}