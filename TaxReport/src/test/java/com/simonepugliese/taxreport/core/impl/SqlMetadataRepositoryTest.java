package com.simonepugliese.taxreport.core.impl;

import com.simonepugliese.taxreport.core.domain.DocumentSlot;
import com.simonepugliese.taxreport.core.domain.ExpenseEntry;
import com.simonepugliese.taxreport.core.dto.DocType;
import com.simonepugliese.taxreport.core.dto.ValidationStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SqlMetadataRepositoryTest {

    @TempDir
    Path tempDir; // Ogni test avrà la sua cartella pulita per il DB

    @Test
    void testInitDbAndPersistence() {
        // 1. Inizializzazione (crea il file .db e le tabelle)
        SqlMetadataRepository repo = new SqlMetadataRepository(tempDir.toString());
        assertTrue(tempDir.resolve("taxreport.db").toFile().exists(), "Il file DB deve essere creato");

        // 2. Creazione Dati
        UUID id = UUID.randomUUID();
        ExpenseEntry entry = new ExpenseEntry(id, "mediche", LocalDate.now(), "Visita Specialistica");
        entry.setPhysicalPath("/2025/mediche/Visita");

        // Aggiungo uno slot VUOTO
        entry.addSlot(new DocumentSlot(DocType.INVOICE, true, "Fattura.pdf"));

        // Aggiungo uno slot PIENO
        DocumentSlot filledSlot = new DocumentSlot(DocType.PAYMENT, false, "Bonifico.pdf");
        filledSlot.fill("Bonifico_Scan.pdf", 2048);
        entry.addSlot(filledSlot);

        // 3. Salvataggio
        repo.save(entry);

        // 4. Ricaricamento (Simuliamo una nuova istanza per forzare la lettura da disco)
        SqlMetadataRepository repoReader = new SqlMetadataRepository(tempDir.toString());
        Optional<ExpenseEntry> foundOpt = repoReader.findById(id);

        // 5. Asserzioni
        assertTrue(foundOpt.isPresent());
        ExpenseEntry loaded = foundOpt.get();

        assertEquals(entry.getId(), loaded.getId());
        assertEquals("mediche", loaded.getCategoryId());
        assertEquals("Visita Specialistica", loaded.getSanitizedDescription()); // O quello che è stato sanitizzato
        assertEquals(ValidationStatus.EMPTY, loaded.getStatus()); // Default

        // Verifica Slot
        assertTrue(loaded.hasSlot(DocType.INVOICE));
        assertFalse(loaded.getSlot(DocType.INVOICE).isFilled());

        assertTrue(loaded.hasSlot(DocType.PAYMENT));
        assertTrue(loaded.getSlot(DocType.PAYMENT).isFilled());

        // Verifica profonda dei metadati del file (il famoso "trucco" della reflection)
        var fileMeta = loaded.getSlot(DocType.PAYMENT).getCurrentFile();
        assertEquals("Bonifico_Scan.pdf", fileMeta.filename());
        assertEquals(2048, fileMeta.size());
        assertNotNull(fileMeta.uploadedAt());
    }

    @Test
    void testUpdateStatusOnly() {
        SqlMetadataRepository repo = new SqlMetadataRepository(tempDir.toString());
        UUID id = UUID.randomUUID();
        ExpenseEntry entry = new ExpenseEntry(id, "test", LocalDate.now(), "Desc");
        repo.save(entry);

        // WHEN: Aggiorno solo lo stato
        repo.updateStatus(id, ValidationStatus.COMPLIANT);

        // THEN: Il reload deve riflettere il cambio
        ExpenseEntry reloaded = repo.findById(id).get();
        assertEquals(ValidationStatus.COMPLIANT, reloaded.getStatus());
    }

    @Test
    void testOverwriteBehavior() {
        // Verifica che il salvataggio sia idempotente (INSERT OR REPLACE)
        SqlMetadataRepository repo = new SqlMetadataRepository(tempDir.toString());
        UUID id = UUID.randomUUID();
        ExpenseEntry entry = new ExpenseEntry(id, "test", LocalDate.now(), "V1");

        repo.save(entry);

        // Modifico l'oggetto e risalvo
        // (Nota: ExpenseEntry non ha setter per desc, ne creo uno nuovo con stesso ID)
        ExpenseEntry entryV2 = new ExpenseEntry(id, "test", LocalDate.now(), "V2");
        repo.save(entryV2);

        assertEquals("V2", repo.findById(id).get().getSanitizedDescription());
    }
}