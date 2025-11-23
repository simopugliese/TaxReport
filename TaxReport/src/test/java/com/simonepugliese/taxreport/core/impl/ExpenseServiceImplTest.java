package com.simonepugliese.taxreport.core.impl;

import com.simonepugliese.taxreport.core.domain.DocumentSlot;
import com.simonepugliese.taxreport.core.domain.ExpenseEntry;
import com.simonepugliese.taxreport.core.domain.RuleEngine;
import com.simonepugliese.taxreport.core.dto.DocType;
import com.simonepugliese.taxreport.core.dto.NewExpenseDTO;
import com.simonepugliese.taxreport.core.dto.ValidationStatus;
import com.simonepugliese.taxreport.core.exception.StorageException;
import com.simonepugliese.taxreport.core.exception.ValidationException;
import com.simonepugliese.taxreport.core.spi.MetadataRepository;
import com.simonepugliese.taxreport.core.spi.StorageStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExpenseServiceImplTest {

    @Mock
    private StorageStrategy storage; // Il finto File System
    @Mock
    private MetadataRepository repository; // Il finto DB
    @Mock
    private RuleEngine ruleEngine; // Il finto Rule Engine

    private ExpenseServiceImpl service;

    @BeforeEach
    void setUp() {
        // Iniezione delle dipendenze mockate
        service = new ExpenseServiceImpl(storage, repository, ruleEngine);
    }

    @Test
    @DisplayName("Create Expense: Successo - Crea cartella e salva entry")
    void testCreateExpense_HappyPath() {
        // GIVEN
        NewExpenseDTO dto = new NewExpenseDTO(2024, "RSSMRA", "mediche", "2024-10-21", "Dentista");

        // Configuriamo i mock: la cartella non esiste, la creazione va a buon fine
        when(storage.exists(anyString())).thenReturn(false);
        when(storage.createDirectory(anyString())).thenReturn(true);

        // WHEN
        String id = service.createExpense(dto);

        // THEN
        assertNotNull(id);

        // Verifica che abbia provato a creare la cartella col path giusto (sanitizzato)
        // Path atteso: /2024/RSSMRA/mediche/20241021_Dentista
        verify(storage).createDirectory(contains("20241021_Dentista"));

        // Verifica che abbia salvato nel DB e chiamato il rule engine
        verify(repository).save(any(ExpenseEntry.class));
        verify(ruleEngine).applyRules(any(ExpenseEntry.class));
    }

    @Test
    @DisplayName("Create Expense: Collisione - Gestisce duplicati con suffisso _1")
    void testCreateExpense_CollisionHandling() {
        NewExpenseDTO dto = new NewExpenseDTO(2024, "RSSMRA", "mediche", "2024-10-21", "Dentista");
        String basePath = "/2024/RSSMRA/mediche/20241021_Dentista";

        // GIVEN
        // La prima volta exists() ritorna true (collisione), la seconda false (suffisso libero)
        when(storage.exists(basePath)).thenReturn(true);
        when(storage.exists(basePath + "_1")).thenReturn(false);
        when(storage.createDirectory(anyString())).thenReturn(true);

        // WHEN
        service.createExpense(dto);

        // THEN
        // Deve aver creato la cartella col suffisso _1
        verify(storage).createDirectory(eq(basePath + "_1"));
    }

    @Test
    @DisplayName("Upload Document: Successo - Aggiorna stato a COMPLIANT")
    void testUploadDocument_Success() {
        UUID entryId = UUID.randomUUID();
        ExpenseEntry mockEntry = new ExpenseEntry(entryId, "mediche", LocalDate.now(), "Test");
        mockEntry.setPhysicalPath("/path/to/entry");

        // Simuliamo che l'entry abbia uno slot vuoto per la FATTURA
        DocumentSlot slot = new DocumentSlot(DocType.INVOICE, true, "Fattura.pdf");
        mockEntry.addSlot(slot);

        // GIVEN
        when(repository.findById(entryId)).thenReturn(Optional.of(mockEntry));
        when(ruleEngine.getStandardFilename(DocType.INVOICE)).thenReturn("Fattura.pdf");
        // Simuliamo che dopo la validazione, il RuleEngine dica "Tutto OK"
        when(ruleEngine.validate(mockEntry)).thenReturn(ValidationStatus.COMPLIANT);

        InputStream dummyStream = new ByteArrayInputStream("DATA".getBytes());

        // WHEN
        service.uploadDocument(entryId.toString(), DocType.INVOICE, dummyStream);

        // THEN
        // 1. Ha salvato il file fisico?
        verify(storage).saveFile(eq("/path/to/entry"), eq("Fattura.pdf"), any());

        // 2. Ha aggiornato lo slot?
        assertTrue(mockEntry.getSlot(DocType.INVOICE).isFilled());

        // 3. Ha aggiornato lo stato su DB?
        verify(repository).updateStatus(entryId, ValidationStatus.COMPLIANT);
        verify(repository).save(mockEntry);
    }

    @Test
    @DisplayName("Upload Document: Errore - Blocca modifica se anno LOCKED")
    void testUploadDocument_ThrowsIfLocked() {
        UUID entryId = UUID.randomUUID();
        ExpenseEntry lockedEntry = new ExpenseEntry(entryId, "mediche", LocalDate.now(), "Test");
        lockedEntry.setStatus(ValidationStatus.LOCKED); // Già chiuso!

        when(repository.findById(entryId)).thenReturn(Optional.of(lockedEntry));

        // WHEN & THEN
        assertThrows(ValidationException.class, () ->
                service.uploadDocument(entryId.toString(), DocType.INVOICE, InputStream.nullInputStream())
        );

        // Assicurati che non abbia toccato lo storage
        verifyNoInteractions(storage);
    }

    @Test
    @DisplayName("Create Expense: Fallimento Storage - Rilancia StorageException")
    void testCreateExpense_StorageFailure() {
        NewExpenseDTO dto = new NewExpenseDTO(2024, "RSSMRA", "mediche", "2024-10-21", "Dentista");

        when(storage.exists(anyString())).thenReturn(false);
        // Simuliamo disco rotto o permessi negati
        when(storage.createDirectory(anyString())).thenThrow(new RuntimeException("Disk Full"));

        assertThrows(StorageException.class, () -> service.createExpense(dto));
    }
}