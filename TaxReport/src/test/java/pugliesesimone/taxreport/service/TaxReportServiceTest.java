package pugliesesimone.taxreport.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pugliesesimone.taxreport.exception.PersonNotFoundException;
import pugliesesimone.taxreport.exception.ServiceException;
import pugliesesimone.taxreport.metadata.MetadataInterface;
import pugliesesimone.taxreport.model.*;
import pugliesesimone.taxreport.storage.StorageInterface;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaxReportServiceTest {

    @Mock StorageInterface storage;
    @Mock MetadataInterface metadata;

    TaxReportService service;
    Expense sampleExpense;

    @BeforeEach
    void setUp() {
        service = new TaxReportService(storage, metadata);

        // Setup dati di prova
        Person person = new Person("Mario Rossi", "RSSMRA80A01H501U");
        sampleExpense = new Expense("2024", person, ExpenseType.VISITA_MEDICA, "Visita Occhi", "10/01/2024");

        // --- MOCK PER RULE ENGINE ---
        // 1. Simuliamo che la cartella config esista per evitare chiamate extra a createFolder
        lenient().when(storage.existsFolder("config")).thenReturn(true);

        // 2. Forniamo un JSON valido vuoto per evitare crash durante la validazione automatica
        lenient().when(storage.loadFile(eq("config"), anyString()))
                .thenReturn(new ByteArrayInputStream("{}".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void registerExpense_ShouldSaveFilesAndMetadata_WhenAllGoesWell() {
        // Arrange
        Attachment att = new Attachment(DocumentType.FATTURA, "fattura.pdf", new ByteArrayInputStream(new byte[0]));
        when(storage.createFolder(anyString())).thenReturn(true);
        when(storage.saveFile(anyString(), anyString(), any())).thenReturn(true);

        lenient().when(metadata.findById(any())).thenReturn(Optional.empty());

        // Act
        service.registerExpense(sampleExpense, List.of(att));

        // Assert
        // 1. Verifica creazione cartella
        ArgumentCaptor<String> pathCaptor = ArgumentCaptor.forClass(String.class);
        verify(storage).createFolder(pathCaptor.capture());

        String path = pathCaptor.getValue();
        assertTrue(path.contains("2024"));
        assertTrue(path.contains("RSSMRA80A01H501U"));

        // 2. Verifica salvataggio file
        verify(storage).saveFile(eq(path), endsWith("_fattura.pdf"), any());

        // 3. Verifica salvataggio su DB
        // FIX: Usiamo atLeastOnce() perché ora il service salva due volte:
        // una volta per la creazione iniziale e una per l'aggiornamento automatico dello stato (Compliance)
        verify(metadata, atLeastOnce()).save(sampleExpense);
    }

    @Test
    void registerExpense_ShouldRollbackFiles_WhenDbFails() {
        // Arrange
        Attachment att = new Attachment(DocumentType.RICEVUTA_PAGAMENTO, "scontrino.jpg", new ByteArrayInputStream(new byte[0]));

        when(storage.createFolder(anyString())).thenReturn(true);
        when(storage.saveFile(anyString(), anyString(), any())).thenReturn(true);

        // Simuliamo il fallimento del DB
        doThrow(new PersonNotFoundException("Persona non esiste")).when(metadata).save(any());

        lenient().when(metadata.findById(any())).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ServiceException.class, () -> {
            service.registerExpense(sampleExpense, List.of(att));
        });

        // Verify Rollback
        verify(storage).deleteFile(anyString(), endsWith("_scontrino.jpg"));
    }

    @Test
    void registerExpense_ShouldKeepAccents_WhenSanitizing() {
        // Arrange
        String descWithAccent = "Pagamento Università";
        sampleExpense.setDescription(descWithAccent);

        Attachment att = new Attachment(DocumentType.FATTURA, "caffè.pdf", new ByteArrayInputStream(new byte[0]));

        when(storage.createFolder(anyString())).thenReturn(true);
        when(storage.saveFile(anyString(), anyString(), any())).thenReturn(true);

        lenient().when(metadata.findById(any())).thenReturn(Optional.empty());

        // Act
        service.registerExpense(sampleExpense, List.of(att));

        // Assert
        ArgumentCaptor<String> pathCaptor = ArgumentCaptor.forClass(String.class);
        verify(storage).createFolder(pathCaptor.capture());

        String path = pathCaptor.getValue();
        assertTrue(path.contains("Pagamento_Università"));

        verify(storage).saveFile(anyString(), endsWith("_caffè.pdf"), any());
    }
}