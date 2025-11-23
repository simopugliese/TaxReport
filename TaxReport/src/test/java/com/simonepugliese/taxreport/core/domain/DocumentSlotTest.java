package com.simonepugliese.taxreport.core.domain;

import com.simonepugliese.taxreport.core.dto.DocType;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DocumentSlotTest {

    @Test
    void testFillAndClear() {
        DocumentSlot slot = new DocumentSlot(DocType.INVOICE, true, "Fattura.pdf");

        assertFalse(slot.isFilled(), "Lo slot deve nascere vuoto");
        assertNull(slot.getCurrentFile());

        // Simula riempimento
        slot.fill("Fattura_Real.pdf", 1024);

        assertTrue(slot.isFilled(), "Lo slot deve risultare pieno");
        assertEquals("Fattura_Real.pdf", slot.getCurrentFile().filename());

        //TODO: Simula svuotamento (opzionale, ma utile se implementerai la cancellazione)
        // Nota: Nel codice attuale 'clear()' è package-private.
        // Se vuoi testarlo qui devi renderlo public o mettere il test nello stesso package.
    }
}