package pugliesesimone.taxreport.rules;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pugliesesimone.taxreport.model.DocumentType;
import pugliesesimone.taxreport.model.ExpenseType;
import pugliesesimone.taxreport.storage.StorageInterface;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RuleEngineTest {

    @Mock
    StorageInterface storage;

    RuleEngine ruleEngine;

    @BeforeEach
    void setUp() {
        ruleEngine = new RuleEngine(storage);
    }

    @Test
    void getMandatoryDocuments_ShouldReturnList_WhenJsonExists() throws IOException {
        // Arrange: Simuliamo un JSON valido nello storage
        String jsonContent = """
                {
                  "VISITA_MEDICA": ["FATTURA", "RICETTA_MEDICA"],
                  "PAGAMENTO_UNIVERSITARIO": ["RICEVUTA_PAGAMENTO"]
                }
                """;
        when(storage.loadFile(anyString(), eq("rules_2024.json")))
                .thenReturn(new ByteArrayInputStream(jsonContent.getBytes(StandardCharsets.UTF_8)));

        // Act
        List<DocumentType> docs = ruleEngine.getMandatoryDocuments("2024", ExpenseType.VISITA_MEDICA);

        // Assert
        assertNotNull(docs);
        assertEquals(2, docs.size());
        assertTrue(docs.contains(DocumentType.FATTURA));
        assertTrue(docs.contains(DocumentType.RICETTA_MEDICA));
    }

    @Test
    void getMandatoryDocuments_ShouldReturnEmpty_WhenFileIsMissing() {
        // Arrange: Simuliamo eccezione (file non trovato)
        when(storage.loadFile(anyString(), anyString())).thenThrow(new RuntimeException("File not found"));

        // Act
        List<DocumentType> docs = ruleEngine.getMandatoryDocuments("2024", ExpenseType.VISITA_VETERINARIA);

        // Assert
        assertNotNull(docs);
        assertTrue(docs.isEmpty(), "Se il file manca, deve tornare lista vuota, non crashare");
    }

    @Test
    void getMandatoryDocuments_ShouldUseCache_WhenCalledTwice() throws IOException {
        // Arrange
        String jsonContent = "{ \"VISITA_MEDICA\": [\"FATTURA\"] }";
        when(storage.loadFile(anyString(), anyString()))
                .thenReturn(new ByteArrayInputStream(jsonContent.getBytes(StandardCharsets.UTF_8)));

        // Act
        ruleEngine.getMandatoryDocuments("2024", ExpenseType.VISITA_MEDICA);
        ruleEngine.getMandatoryDocuments("2024", ExpenseType.VISITA_MEDICA); // Seconda chiamata

        // Assert
        // Verify che loadFile sia stato chiamato UNA SOLA VOLTA (grazie alla cache)
        verify(storage, times(1)).loadFile(anyString(), eq("rules_2024.json"));
    }

    @Test
    void getMandatoryDocuments_ShouldReturnEmpty_WhenJsonIsCorrupt() throws IOException {
        // Arrange: JSON malformato
        String badJson = "{ \"VISITA_MEDICA\": [\"FATTURA\" ... ops rotto";
        when(storage.loadFile(anyString(), anyString()))
                .thenReturn(new ByteArrayInputStream(badJson.getBytes(StandardCharsets.UTF_8)));

        // Act
        List<DocumentType> docs = ruleEngine.getMandatoryDocuments("2024", ExpenseType.VISITA_MEDICA);

        // Assert
        assertTrue(docs.isEmpty(), "Se il JSON è rotto, deve fallire silentemente e tornare lista vuota");
    }
}