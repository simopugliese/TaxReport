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


//TODO: andrebbe implementato e testato bene, qui è solo parziale e a scopo dimostrativo
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

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS persons (
                    id TEXT PRIMARY KEY,
                    name TEXT NOT NULL,
                    fiscal_code TEXT NOT NULL
                )""");

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
            conn.setAutoCommit(false);

            try {
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

                try (PreparedStatement ps = conn.prepareStatement(sqlDeleteDocs)) {
                    ps.setString(1, expense.getId().toString());
                    ps.executeUpdate();
                }

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
                try { conn.rollback(); } catch (SQLException ex) {}

                if (e.getErrorCode() == 19) {
                    throw new PersonNotFoundException("Impossibile salvare la spesa: La persona con ID "
                            + expense.getPerson().getId() + " non esiste nel database.");
                }
                throw e;
            }

        } catch (SQLException e) {
            throw new StorageException("Errore salvataggio spesa: " + expense.getId(), e);
        }
    }

    @Override
    public void saveAll(List<Expense> expenses) {
        for (Expense e : expenses) {
            save(e);
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
        try (Connection conn = DriverManager.getConnection(connectionString);
             PreparedStatement ps = conn.prepareStatement("INSERT OR IGNORE INTO persons (id, name, fiscal_code) VALUES (?, ?, ?)")) {
            ps.setString(1, person.getId().toString());
            ps.setString(2, person.getName());
            ps.setString(3, person.getFiscalCode());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new StorageException("Errore savePerson SQLite", e);
        }
    }

    @Override
    public List<Person> findAllPersons() {
        return List.of();
    }

    @Override
    public List<Expense> findByYear(String year) {
        return findByYear(year, -1, -1);
    }

    @Override
    public List<Expense> findByYear(String year, int limit, int offset) {
        return new ArrayList<>();
    }

    @Override
    public List<String> getAvailableYears() {
        List<String> years = new ArrayList<>();
        String sql = "SELECT DISTINCT year FROM expenses ORDER BY year DESC";
        try (Connection conn = DriverManager.getConnection(connectionString);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                years.add(rs.getString("year"));
            }
        } catch (SQLException e) {
            throw new StorageException("Errore caricamento anni", e);
        }
        return years;
    }
}
