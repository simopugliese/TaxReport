package pugliesesimone.taxreport.rules;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pugliesesimone.taxreport.metadata.MetadataInterface;
import pugliesesimone.taxreport.model.*;

import java.util.List;

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

        Person p = new Person("Mario", "CF123");
        expense = new Expense("2024", p, ExpenseType.VISITA_MEDICA, "Test", "01/01/2024");
    }

    @Test
    void checkCompliance_ShouldBeTrue_WhenAllDocsPresent() {

        when(ruleEngine.getMandatoryDocuments("2024", ExpenseType.VISITA_MEDICA))
                .thenReturn(List.of(DocumentType.FATTURA));

        expense.addDocument(new Document(DocumentType.FATTURA, "path/f.pdf"));

        ComplianceResult res = service.checkCompliance(expense);

        assertTrue(res.isCompliant());
        assertTrue(res.getMissingDocuments().isEmpty());
    }

    @Test
    void checkCompliance_ShouldBeFalse_WhenDocMissing() {
        when(ruleEngine.getMandatoryDocuments("2024", ExpenseType.VISITA_MEDICA))
                .thenReturn(List.of(DocumentType.FATTURA, DocumentType.PRESCRIZIONE_MEDICA));

        expense.addDocument(new Document(DocumentType.FATTURA, "path/f.pdf"));

        ComplianceResult res = service.checkCompliance(expense);

        assertFalse(res.isCompliant());
        assertEquals(1, res.getMissingDocuments().size());
        assertEquals(DocumentType.PRESCRIZIONE_MEDICA, res.getMissingDocuments().get(0));
    }

    @Test
    void validateAndUpdateStatus_ShouldSave_WhenStatusChanges() {
        expense.setExpenseState(ExpenseState.INITIAL);
        expense.addDocument(new Document(DocumentType.FATTURA, "path.pdf"));

        when(ruleEngine.getMandatoryDocuments(anyString(), any())).thenReturn(List.of(DocumentType.FATTURA));

        service.validateAndUpdateStatus(List.of(expense));

        assertEquals(ExpenseState.COMPLETED, expense.getExpenseState());
        verify(metadata, times(1)).saveAll(anyList());
    }

    @Test
    void validateAndUpdateStatus_ShouldNotSave_WhenStatusIsAlreadyCorrect() {
        expense.setExpenseState(ExpenseState.COMPLETED);
        expense.addDocument(new Document(DocumentType.FATTURA, "path.pdf"));

        when(ruleEngine.getMandatoryDocuments(anyString(), any())).thenReturn(List.of(DocumentType.FATTURA));

        service.validateAndUpdateStatus(List.of(expense));

        verify(metadata, never()).saveAll(anyList());
    }

    @Test
    void validateAndUpdateStatus_ShouldIgnore_WhenBlocked() {
        expense.setExpenseState(ExpenseState.BLOCKED);

        service.validateAndUpdateStatus(List.of(expense));

        verify(ruleEngine, never()).getMandatoryDocuments(anyString(), any());
        verify(metadata, never()).saveAll(anyList());
    }
}