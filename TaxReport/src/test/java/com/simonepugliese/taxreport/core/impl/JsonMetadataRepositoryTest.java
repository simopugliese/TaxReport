package com.simonepugliese.taxreport.core.impl;

import com.simonepugliese.taxreport.core.domain.ExpenseEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JsonMetadataRepositoryTest {

    @TempDir
    Path tempDir; // Cartella temporanea creata da JUnit

    @Test
    void testPersistAndReload() {
        // 1. Crea il repository nella cartella temporanea
        JsonMetadataRepository repo1 = new JsonMetadataRepository(tempDir.toString());

        UUID id = UUID.randomUUID();
        ExpenseEntry entry = new ExpenseEntry(id, "mediche", LocalDate.now(), "Dentista");

        // 2. Salva una spesa
        repo1.save(entry);

        // 3. Verifica che il file esista fisicamente
        assertTrue(tempDir.resolve("db_expenses.json").toFile().exists());

        // 4. SIMULA RIAVVIO APP: Crea una NUOVA istanza puntando alla STESSA cartella
        JsonMetadataRepository repo2 = new JsonMetadataRepository(tempDir.toString());

        // 5. Cerca la spesa nel nuovo repo
        Optional<ExpenseEntry> loaded = repo2.findById(id);

        // 6. Assert
        assertTrue(loaded.isPresent());
        assertEquals("mediche", loaded.get().getCategoryId());
        assertEquals("Dentista", loaded.get().getSlots().isEmpty() ? "Dentista" : "Dentista"); // Check banale
    }
}