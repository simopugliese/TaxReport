package pugliesesimone.taxreport.metadata;

import pugliesesimone.taxreport.exception.ConfigurationException;
import pugliesesimone.taxreport.exception.PersonNotFoundException;
import pugliesesimone.taxreport.exception.StorageException;
import pugliesesimone.taxreport.model.*;

import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class SQLiteMetadata implements MetadataInterface {
    private final String connectionString;

    public SQLiteMetadata(String rootPath) {
        if (rootPath == null || rootPath.isBlank()) {
            throw new ConfigurationException("Il path di root per il database non può essere vuoto.");
        }

        File rootDir = new File(rootPath);
        if (!rootDir.exists() || !rootDir.isDirectory()) {
            throw new ConfigurationException("La directory di root specificata non esiste o non è valida: " + rootPath);
        }

        File dbFile = new File(rootPath, "taxreport.db");
        this.connectionString = "jdbc:sqlite:" + dbFile.getAbsolutePath() + "?foreign_keys=on";

        try {
            initDatabase();
        } catch (StorageException e) {
            throw new ConfigurationException("Impossibile inizializzare lo schema del database.", e);
        }
    }

    private void initDatabase() {
        try (Connection conn = DriverManager.getConnection(connectionString);
             Statement stmt = conn.createStatement()) {

            // 1. Tabella Persone (Lookup)
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS persons (
                    id TEXT PRIMARY KEY,
                    name TEXT NOT NULL,
                    fiscal_code TEXT NOT NULL
                )""");

            // 2. Tabella Spese
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS expenses (
                    id TEXT PRIMARY KEY,
                    year TEXT NOT NULL,
                    person_id TEXT NOT NULL,
                    type TEXT NOT NULL,
                    description TEXT,
                    raw_date TEXT,
                    state TEXT NOT NULL,
                    FOREIGN KEY(person_id) REFERENCES persons(id)
                )""");

            // 3. Tabella Documenti (ex Slots)
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS documents (
                    id TEXT PRIMARY KEY,
                    expense_id TEXT NOT NULL,
                    doc_type TEXT NOT NULL,
                    relative_path TEXT,
                    FOREIGN KEY(expense_id) REFERENCES expenses(id)
                )""");

        } catch (SQLException e) {
            throw new StorageException("Impossibile inizializzare il database SQLite", e);
        }
    }

    @Override
    public void save(Expense expense) {
        String sqlExpense = """
            INSERT OR REPLACE INTO expenses
            (id, year, person_id, type, description, raw_date, state)
            VALUES (?, ?, ?, ?, ?, ?, ?)""";

        String sqlDeleteDocs = "DELETE FROM documents WHERE expense_id = ?";

        String sqlInsertDoc = """
            INSERT INTO documents (id, expense_id, doc_type, relative_path)
            VALUES (?, ?, ?, ?)""";

        try (Connection conn = DriverManager.getConnection(connectionString)) {
            conn.setAutoCommit(false); // Start Transaction

            try {
                // 1. Salva Header Spesa
                try (PreparedStatement ps = conn.prepareStatement(sqlExpense)) {
                    ps.setString(1, expense.getId().toString());
                    ps.setString(2, expense.getYear());
                    ps.setString(3, expense.getPerson().getId().toString());
                    ps.setString(4, expense.getExpenseType().name());
                    ps.setString(5, expense.getDescription());
                    ps.setString(6, expense.getRawDate());
                    ps.setString(7, expense.getExpenseState().name());
                    ps.executeUpdate();
                }

                // 2. Elimina vecchi documenti
                try (PreparedStatement ps = conn.prepareStatement(sqlDeleteDocs)) {
                    ps.setString(1, expense.getId().toString());
                    ps.executeUpdate();
                }

                // 3. Inserisci Documenti attuali
                if (!expense.getDocuments().isEmpty()) {
                    try (PreparedStatement ps = conn.prepareStatement(sqlInsertDoc)) {
                        for (Document doc : expense.getDocuments()) {
                            ps.setString(1, doc.getId().toString());
                            ps.setString(2, expense.getId().toString());
                            ps.setString(3, doc.getDocumentType().name());
                            ps.setString(4, doc.getRelativePath());
                            ps.addBatch();
                        }
                        ps.executeBatch();
                    }
                }

                conn.commit();

            } catch (SQLException e) {
                // Rollback esplicito in caso di errore
                try { conn.rollback(); } catch (SQLException ex) { /* Logga errore rollback */ }

                if (e.getErrorCode() == 19) { // SQLite Error Code 19 = SQLITE_CONSTRAINT
                    throw new PersonNotFoundException("Impossibile salvare la spesa: La persona con ID "
                            + expense.getPerson().getId() + " non esiste nel database.");
                }
                throw e;
            }

        } catch (SQLException e) {
            throw new StorageException("Errore salvataggio spesa: " + expense.getId(), e);
        }
    }

    public Optional<Expense> findById(UUID id) {
        String sqlExpense = """
            SELECT e.*, p.name as person_name, p.fiscal_code as person_fc
            FROM expenses e
            JOIN persons p ON e.person_id = p.id
            WHERE e.id = ?""";

        String sqlDocs = "SELECT * FROM documents WHERE expense_id = ?";

        Expense expense = null;

        try (Connection conn = DriverManager.getConnection(connectionString)) {

            // 1. Carica Expense + Person
            try (PreparedStatement ps = conn.prepareStatement(sqlExpense)) {
                ps.setString(1, id.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        Person person = new Person(
                                UUID.fromString(rs.getString("person_id")),
                                rs.getString("person_name"),
                                rs.getString("person_fc")
                        );

                        expense = new Expense(
                                UUID.fromString(rs.getString("id")),
                                rs.getString("year"),
                                person,
                                ExpenseType.valueOf(rs.getString("type")),
                                rs.getString("description"),
                                rs.getString("raw_date"),
                                ExpenseState.valueOf(rs.getString("state"))
                        );
                    } else {
                        return Optional.empty();
                    }
                }
            }

            // 2. Carica Documenti
            List<Document> docs = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(sqlDocs)) {
                ps.setString(1, id.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Document doc = new Document(
                                UUID.fromString(rs.getString("id")),
                                DocumentType.valueOf(rs.getString("doc_type")),
                                rs.getString("relative_path")
                        );
                        docs.add(doc);
                    }
                }
            }

            expense.setDocuments(docs);

            return Optional.of(expense);

        } catch (SQLException e) {
            throw new StorageException("Errore caricamento spesa: " + id, e);
        }
    }

    @Override
    public void savePerson(Person person) {
        return; //todo
    }

    @Override
    public List<Expense> findByYear(String year) {
        return List.of();
    }
}