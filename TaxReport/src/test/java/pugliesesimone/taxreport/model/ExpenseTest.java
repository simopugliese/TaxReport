package pugliesesimone.taxreport.model;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ExpenseTest {

    @Test
    void constructor_ShouldGenerateIdAndInitialState() {
        Person p = new Person("Mario", "CF");
        Expense e = new Expense("2024", p, ExpenseType.VISITA_MEDICA, "Desc", "01/01/2024");

        assertNotNull(e.getId());
        assertEquals(ExpenseState.INITIAL, e.getExpenseState());
        assertTrue(e.getDocuments().isEmpty());
    }

    @Test
    void setDocuments_ShouldReplaceList() {
        Person p = new Person("Mario", "CF");
        Expense e = new Expense("2024", p, ExpenseType.VISITA_MEDICA, "Desc", "01/01/2024");

        Document d1 = new Document(DocumentType.FATTURA, "a.pdf");
        e.addDocument(d1);

        Document d2 = new Document(DocumentType.RICEVUTA_PAGAMENTO, "b.pdf");

        // Act: setDocuments deve svuotare e riempire
        e.setDocuments(List.of(d2));

        // Assert
        assertEquals(1, e.getDocuments().size());
        assertEquals(DocumentType.RICEVUTA_PAGAMENTO, e.getDocuments().iterator().next().getDocumentType());
    }

    @Test
    void removeDocumentById_ShouldRemoveCorrectDocument() {
        Person p = new Person("Mario", "CF");
        Expense e = new Expense("2024", p, ExpenseType.VISITA_MEDICA, "Desc", "01/01/2024");

        Document doc1 = new Document(DocumentType.FATTURA, "a.pdf");
        Document doc2 = new Document(DocumentType.PRESCRIZIONE_MEDICA, "b.pdf");

        e.addDocument(doc1);
        e.addDocument(doc2);

        // Act
        boolean removed = e.removeDocumentById(doc1.getId());

        // Assert
        assertTrue(removed);
        assertEquals(1, e.getDocuments().size());
        assertEquals(doc2.getId(), e.getDocuments().iterator().next().getId());
    }
}