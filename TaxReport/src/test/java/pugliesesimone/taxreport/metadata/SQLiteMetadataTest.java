package pugliesesimone.taxreport.metadata;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pugliesesimone.taxreport.exception.PersonNotFoundException;
import pugliesesimone.taxreport.model.*;

import java.io.File;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SQLiteMetadataTest {

    @TempDir
    Path tempDir;

    SQLiteMetadata metadata;
    Person testPerson;

    @BeforeEach
    void setUp() throws SQLException {
        metadata = new SQLiteMetadata(tempDir.toString());

        testPerson = new Person("Mario Rossi", "RSSMRA80A01H501U");

        bootstrapPerson(testPerson);
    }

    @AfterEach
    void tearDown() {}

    @Test
    void init_ShouldCreateDatabaseFile() {
        File dbFile = tempDir.resolve("taxreport.db").toFile();
        assertTrue(dbFile.exists(), "Il file del database deve essere creato all'inizializzazione");
    }

    @Test
    void save_ShouldPersistExpenseAndDocuments() {
        Expense expense = new Expense("2024", testPerson, ExpenseType.VISITA_MEDICA, "Occhiali", "01/01/2024");
        Document doc1 = new Document(DocumentType.FATTURA, "2024/doc1.pdf");
        Document doc2 = new Document(DocumentType.RICEVUTA_PAGAMENTO, "2024/doc2.pdf");
        expense.addDocument(doc1);
        expense.addDocument(doc2);

        metadata.save(expense);

        Optional<Expense> retrievedOpt = metadata.findById(expense.getId());
        assertTrue(retrievedOpt.isPresent());

        Expense retrieved = retrievedOpt.get();
        assertEquals(expense.getDescription(), retrieved.getDescription());
        assertEquals(expense.getExpenseState(), retrieved.getExpenseState());

        assertEquals(2, retrieved.getDocuments().size());
        assertTrue(retrieved.getDocuments().stream()
                .anyMatch(d -> d.getRelativePath().equals("2024/doc1.pdf")));
    }

    @Test
    void save_ShouldUpdateExistingExpense() {
        Expense expense = new Expense("2024", testPerson, ExpenseType.PAGAMENTO_UNIVERSITARIO, "Rata 1", "01/02/2024");
        metadata.save(expense);

        expense.setExpenseState(ExpenseState.COMPLETED);
        expense.setDescription("Rata 1 Saldada");
        metadata.save(expense);

        Expense updated = metadata.findById(expense.getId()).get();
        assertEquals(ExpenseState.COMPLETED, updated.getExpenseState());
        assertEquals("Rata 1 Saldada", updated.getDescription());
    }

    @Test
    void save_ShouldReplaceDocumentsOnUpdate() {
        Expense expense = new Expense("2024", testPerson, ExpenseType.VISITA_VETERINARIA, "Vaccino", "10/05/2024");
        expense.addDocument(new Document(DocumentType.FATTURA, "old_doc.pdf"));
        metadata.save(expense);

        Document newDoc = new Document(DocumentType.RICEVUTA_PAGAMENTO, "new_doc.pdf");
        expense.setDocuments(List.of(newDoc));
        metadata.save(expense);

        Expense updated = metadata.findById(expense.getId()).get();
        assertEquals(1, updated.getDocuments().size());
        assertEquals("new_doc.pdf", updated.getDocuments().iterator().next().getRelativePath());
    }

    @Test
    void save_ShouldThrowException_WhenPersonDoesNotExist() {
        Person ghost = new Person("Fantasma", "FNT000");
        Expense expense = new Expense("2024", ghost, ExpenseType.VISITA_MEDICA, "Errore", "01/01/2024");

        assertThrows(PersonNotFoundException.class, () -> metadata.save(expense));
    }

    @Test
    void findById_ShouldReturnEmpty_WhenIdUnknown() {
        Optional<Expense> res = metadata.findById(UUID.randomUUID());
        assertTrue(res.isEmpty());
    }

    // --- Helper ---
    private void bootstrapPerson(Person p) throws SQLException {
        String url = "jdbc:sqlite:" + tempDir.resolve("taxreport.db").toAbsolutePath();
        try (Connection conn = DriverManager.getConnection(url);
             PreparedStatement ps = conn.prepareStatement("INSERT INTO persons (id, name, fiscal_code) VALUES (?, ?, ?)")) {
            ps.setString(1, p.getId().toString());
            ps.setString(2, p.getName());
            ps.setString(3, p.getFiscalCode());
            ps.executeUpdate();
        }
    }
}