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
import java.util.List;

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
    }

    @Test
    void registerExpense_ShouldSaveFilesAndMetadata_WhenAllGoesWell() {
        // Arrange
        Attachment att = new Attachment(DocumentType.FATTURA, "fattura.pdf", new ByteArrayInputStream(new byte[0]));
        when(storage.createFolder(anyString())).thenReturn(true);
        when(storage.saveFile(anyString(), anyString(), any())).thenReturn(true);

        // Act
        service.registerExpense(sampleExpense, List.of(att));

        // Assert
        // 1. Verifica creazione cartella con struttura corretta (UUID incluso)
        ArgumentCaptor<String> pathCaptor = ArgumentCaptor.forClass(String.class);
        verify(storage).createFolder(pathCaptor.capture());

        String path = pathCaptor.getValue();
        // Controlla che il path contenga le parti chiave
        assertTrue(path.contains("2024"));
        assertTrue(path.contains("RSSMRA80A01H501U"));
        assertTrue(path.contains("VISITA_MEDICA"));
        assertTrue(path.contains("10_01_2024_Visita_Occhi_" + sampleExpense.getId()));

        // 2. Verifica salvataggio file
        verify(storage).saveFile(eq(path), eq("fattura.pdf"), any());

        // 3. Verifica salvataggio su DB
        verify(metadata).save(sampleExpense);
    }

    @Test
    void registerExpense_ShouldRollbackFiles_WhenDbFails() {
        // Arrange: Simuliamo il DB che fallisce (es. Persona non trovata)
        Attachment att = new Attachment(DocumentType.SCONTRINO, "scontrino.jpg", new ByteArrayInputStream(new byte[0]));

        when(storage.createFolder(anyString())).thenReturn(true);
        when(storage.saveFile(anyString(), anyString(), any())).thenReturn(true);
        doThrow(new PersonNotFoundException("Persona non esiste")).when(metadata).save(any());

        // Act & Assert
        assertThrows(ServiceException.class, () -> {
            service.registerExpense(sampleExpense, List.of(att));
        });

        // Verify Rollback: deve aver chiamato deleteFile
        verify(storage).deleteFile(anyString(), eq("scontrino.jpg"));
    }

    @Test
    void registerExpense_ShouldKeepAccents_WhenSanitizing() {
        // Arrange
        String descWithAccent = "Pagamento Università";
        sampleExpense.setDescription(descWithAccent);

        Attachment att = new Attachment(DocumentType.FATTURA, "caffè.pdf", new ByteArrayInputStream(new byte[0]));

        when(storage.createFolder(anyString())).thenReturn(true);
        when(storage.saveFile(anyString(), anyString(), any())).thenReturn(true);

        // Act
        service.registerExpense(sampleExpense, List.of(att));

        // Assert
        ArgumentCaptor<String> pathCaptor = ArgumentCaptor.forClass(String.class);
        verify(storage).createFolder(pathCaptor.capture());

        String path = pathCaptor.getValue();
        // Verifica che la 'à' sia rimasta e lo spazio sia diventato '_'
        assertTrue(path.contains("Pagamento_Università"), "La descrizione sanitizzata dovrebbe contenere 'Università'");

        // Verifica anche il nome del file
        verify(storage).saveFile(anyString(), eq("caffè.pdf"), any());
    }
}