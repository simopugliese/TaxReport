package com.simonepugliese.taxreport.core.domain;

import com.simonepugliese.taxreport.core.dto.DocType;
import com.simonepugliese.taxreport.core.dto.ValidationStatus;
import com.simonepugliese.taxreport.core.exception.ConfigurationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RuleEngineTest {

    private RuleEngine ruleEngine;

    @BeforeEach
    void setUp() {
        ruleEngine = new RuleEngine();
    }

    @Test
    void testLoadRules_Success() {
        // Carica il file rules_9999.json dai resources di test
        assertDoesNotThrow(() -> ruleEngine.loadRules(9999));
    }

    @Test
    void testLoadRules_FileNotFound() {
        assertThrows(ConfigurationException.class, () -> ruleEngine.loadRules(1000));
    }

    @Test
    void testApplyRules() {
        ruleEngine.loadRules(9999);

        // FIX: Usiamo una data fissa (9999) invece di LocalDate.now()
        // Altrimenti cerca rules_2024.json o rules_2025.json e fallisce
        LocalDate fixedDate = LocalDate.of(9999, 1, 1);
        ExpenseEntry entry = new ExpenseEntry(UUID.randomUUID(), "test_cat", fixedDate, "Desc");

        ruleEngine.applyRules(entry);

        assertTrue(entry.hasSlot(DocType.INVOICE));
        assertEquals("Fattura.pdf", entry.getSlot(DocType.INVOICE).getExpectedFilename());
    }

    @Test
    void testValidate_Logic() {
        ruleEngine.loadRules(9999);

        // FIX: Anche qui data fissa allineata alle regole caricate
        LocalDate fixedDate = LocalDate.of(9999, 1, 1);
        ExpenseEntry entry = new ExpenseEntry(UUID.randomUUID(), "test_cat", fixedDate, "Desc");

        ruleEngine.applyRules(entry);

        // Caso 1: EMPTY
        assertEquals(ValidationStatus.EMPTY, ruleEngine.validate(entry));

        // Caso 2: COMPLIANT
        entry.getSlot(DocType.INVOICE).fill("Fattura.pdf", 100);
        assertEquals(ValidationStatus.COMPLIANT, ruleEngine.validate(entry));
    }
}