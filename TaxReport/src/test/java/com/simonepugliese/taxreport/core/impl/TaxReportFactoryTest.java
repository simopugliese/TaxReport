package com.simonepugliese.taxreport.core.impl;

import com.simonepugliese.taxreport.core.api.ExpenseService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class TaxReportFactoryTest {

    @Test
    void testCreateLocalService(@TempDir Path tempPath) {
        // Simple Smoke Test: verifica che la factory non esploda e ritorni un'istanza valida
        ExpenseService service = TaxReportFactory.createLocalService(tempPath.toString());

        assertNotNull(service);
        // Verifica indiretta che sia l'implementazione giusta
        assertTrue(service instanceof ExpenseServiceImpl);
    }
}