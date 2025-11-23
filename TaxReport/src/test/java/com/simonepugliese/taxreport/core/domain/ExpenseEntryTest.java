package com.simonepugliese.taxreport.core.domain;

import com.simonepugliese.taxreport.core.dto.DocType;
import com.simonepugliese.taxreport.core.dto.ValidationStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ExpenseEntryTest {

    @Test
    void testEntryInitialization() {
        UUID id = UUID.randomUUID();
        ExpenseEntry entry = new ExpenseEntry(id, "mediche", LocalDate.now(), "Dentista");

        assertEquals(ValidationStatus.EMPTY, entry.getStatus());
        assertTrue(entry.getSlots().isEmpty());
    }

    @Test
    void testSlotManagement() {
        ExpenseEntry entry = new ExpenseEntry(UUID.randomUUID(), "mediche", LocalDate.now(), "Dentista");
        DocumentSlot slot = new DocumentSlot(DocType.RECEIPT, true, "Scontrino.pdf");

        entry.addSlot(slot);

        assertTrue(entry.hasSlot(DocType.RECEIPT));
        assertNotNull(entry.getSlot(DocType.RECEIPT));
        assertFalse(entry.hasSlot(DocType.INVOICE));
    }

    @Test
    void testImmutabilityOfGetSlots() {
        ExpenseEntry entry = new ExpenseEntry(UUID.randomUUID(), "mediche", LocalDate.now(), "Dentista");
        assertThrows(UnsupportedOperationException.class, () -> entry.getSlots().put(DocType.INVOICE, null));
    }
}