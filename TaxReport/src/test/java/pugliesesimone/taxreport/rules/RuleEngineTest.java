package pugliesesimone.taxreport.rules;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pugliesesimone.taxreport.exception.ConfigurationException;
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
        String jsonContent = """
                {
                  "VISITA_MEDICA": ["FATTURA", "PRESCRIZIONE_MEDICA"],
                  "PAGAMENTO_UNIVERSITARIO": ["RICEVUTA_PAGAMENTO"]
                }
                """;
        when(storage.loadFile(anyString(), eq("rules_2024.json")))
                .thenReturn(new ByteArrayInputStream(jsonContent.getBytes(StandardCharsets.UTF_8)));

        List<DocumentType> docs = ruleEngine.getMandatoryDocuments("2024", ExpenseType.VISITA_MEDICA);

        assertNotNull(docs);
        assertEquals(2, docs.size());
        assertTrue(docs.contains(DocumentType.FATTURA));
        assertTrue(docs.contains(DocumentType.PRESCRIZIONE_MEDICA));

    }

    @Test
    void getMandatoryDocuments_ShouldThrow_WhenFileIsMissing() {
        when(storage.loadFile(anyString(), anyString())).thenThrow(new RuntimeException("File not found"));
        assertThrows(RuntimeException.class, () ->
                ruleEngine.getMandatoryDocuments("2024", ExpenseType.VISITA_VETERINARIA)
        );
    }

    @Test
    void getMandatoryDocuments_ShouldUseCache_WhenCalledTwice() throws IOException {
        String jsonContent = "{ \"VISITA_MEDICA\": [\"FATTURA\"] }";
        when(storage.loadFile(anyString(), anyString()))
                .thenReturn(new ByteArrayInputStream(jsonContent.getBytes(StandardCharsets.UTF_8)));

        ruleEngine.getMandatoryDocuments("2024", ExpenseType.VISITA_MEDICA);
        ruleEngine.getMandatoryDocuments("2024", ExpenseType.VISITA_MEDICA); // Seconda chiamata

        verify(storage, times(1)).loadFile(anyString(), eq("rules_2024.json"));
    }

    @Test
    void getMandatoryDocuments_ShouldThrow_WhenJsonIsCorrupt() throws IOException {
        String badJson = "{ \"VISITA_MEDICA\": [\"FATTURA\" ... ops rotto";
        when(storage.loadFile(anyString(), anyString()))
                .thenReturn(new ByteArrayInputStream(badJson.getBytes(StandardCharsets.UTF_8)));

        assertThrows(ConfigurationException.class, () ->
                ruleEngine.getMandatoryDocuments("2024", ExpenseType.VISITA_MEDICA)
        );
    }
}