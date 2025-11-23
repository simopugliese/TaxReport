package com.simonepugliese.taxreport.core.impl;

import com.simonepugliese.taxreport.core.api.ExpenseService;
import com.simonepugliese.taxreport.core.dto.DocType;
import com.simonepugliese.taxreport.core.dto.NewExpenseDTO;
import com.simonepugliese.taxreport.core.dto.ValidationStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class IntegrationTest {

    @Test
    void testFullFlow(@TempDir Path tempDir) {
        // 1. Crea il servizio VERO (usando la Factory locale su cartella temporanea)
        ExpenseService service = TaxReportFactory.createLocalService(tempDir.toString());

        // Nota: Dobbiamo assicurarci che rules_2024.json sia nel classpath di test
        // Se fallisce qui è perché manca il file in src/test/resources o src/main/resources
        assertDoesNotThrow(() -> service.initYear(2024));

        // 2. Crea Spesa
        String id = service.createExpense(new NewExpenseDTO(2024, "CF123", "spese_mediche", "2024-01-01", "Test Integrazione"));

        // 3. Verifica stato iniziale
        assertEquals(ValidationStatus.EMPTY, service.getStatus(id).status());

        // 4. Carica file
        service.uploadDocument(id, DocType.INVOICE, new ByteArrayInputStream(new byte[10]));

        // 5. Verifica persistenza fisica
        // Il file dovrebbe essere in: tempDir / 2024 / CF123 / spese_mediche / 20240101_Test_Integrazione / Fattura.pdf
        // (Il path esatto dipende dalla logica, ma verifichiamo almeno che non sia vuoto)
        assertTrue(service.getStatus(id).slotsFilled().get(DocType.INVOICE));

        // 6. Verifica persistenza dati (JSON)
        // Se ricreo il servizio sulla stessa cartella, deve ritrovare i dati
        ExpenseService serviceRestarted = TaxReportFactory.createLocalService(tempDir.toString());
        serviceRestarted.initYear(2024);
        assertEquals(ValidationStatus.PARTIAL, serviceRestarted.getStatus(id).status());
    }
}