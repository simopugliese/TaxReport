package pugliesesimone.taxreport.rules;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pugliesesimone.taxreport.metadata.MetadataInterface;
import pugliesesimone.taxreport.model.*;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ComplianceServiceTest {

    @Mock MetadataInterface metadata;
    @Mock RuleEngine ruleEngine;

    ComplianceService service;
    Expense expense;

    @BeforeEach
    void setUp() {
        service = new ComplianceService(metadata, ruleEngine);

        // Setup base Expense
        Person p = new Person("Mario", "CF123");
        expense = new Expense("2024", p, ExpenseType.VISITA_MEDICA, "Test", "01/01/2024");
    }

    @Test
    void checkCompliance_ShouldBeTrue_WhenAllDocsPresent() {
        // Arrange: Regola vuole FATTURA
        when(ruleEngine.getMandatoryDocuments("2024", ExpenseType.VISITA_MEDICA))
                .thenReturn(List.of(DocumentType.FATTURA));

        // Expense ha FATTURA
        expense.addDocument(new Document(DocumentType.FATTURA, "path/f.pdf"));

        // Act
        ComplianceResult res = service.checkCompliance(expense);

        // Assert
        assertTrue(res.isCompliant());
        assertTrue(res.getMissingDocuments().isEmpty());
    }

    @Test
    void checkCompliance_ShouldBeFalse_WhenDocMissing() {
        // Arrange: Regola vuole FATTURA + RICETTA
        when(ruleEngine.getMandatoryDocuments("2024", ExpenseType.VISITA_MEDICA))
                .thenReturn(List.of(DocumentType.FATTURA, DocumentType.PRESCRIZIONE_MEDICA));

        // Expense ha SOLO FATTURA
        expense.addDocument(new Document(DocumentType.FATTURA, "path/f.pdf"));

        // Act
        ComplianceResult res = service.checkCompliance(expense);

        // Assert
        assertFalse(res.isCompliant());
        assertEquals(1, res.getMissingDocuments().size());
        assertEquals(DocumentType.PRESCRIZIONE_MEDICA, res.getMissingDocuments().get(0));
    }

    @Test
    void validateAndUpdateStatus_ShouldSave_WhenStatusChanges() {
        // Arrange
        // Expense è INITIAL. Regola vuole FATTURA. Expense HA FATTURA.
        // Quindi deve diventare COMPLETED.
        expense.setExpenseState(ExpenseState.INITIAL);
        expense.addDocument(new Document(DocumentType.FATTURA, "path.pdf"));

        when(ruleEngine.getMandatoryDocuments(anyString(), any())).thenReturn(List.of(DocumentType.FATTURA));

        // Act
        service.validateAndUpdateStatus(List.of(expense));

        // Assert
        assertEquals(ExpenseState.COMPLETED, expense.getExpenseState());
        verify(metadata, times(1)).save(expense); // Deve salvare
    }

    @Test
    void validateAndUpdateStatus_ShouldNotSave_WhenStatusIsAlreadyCorrect() {
        // Arrange: Expense è GIÀ COMPLETED e ha tutto ok.
        expense.setExpenseState(ExpenseState.COMPLETED);
        expense.addDocument(new Document(DocumentType.FATTURA, "path.pdf"));

        when(ruleEngine.getMandatoryDocuments(anyString(), any())).thenReturn(List.of(DocumentType.FATTURA));

        // Act
        service.validateAndUpdateStatus(List.of(expense));

        // Assert
        verify(metadata, never()).save(expense); // NON deve salvare (ottimizzazione)
    }

    @Test
    void validateAndUpdateStatus_ShouldIgnore_WhenBlocked() {
        // Arrange: Expense è BLOCKED.
        expense.setExpenseState(ExpenseState.BLOCKED);

        // Act
        service.validateAndUpdateStatus(List.of(expense));

        // Assert
        // Non deve fare check, non deve salvare.
        verify(ruleEngine, never()).getMandatoryDocuments(anyString(), any());
        verify(metadata, never()).save(expense);
    }
}