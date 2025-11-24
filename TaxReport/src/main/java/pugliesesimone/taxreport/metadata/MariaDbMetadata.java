package pugliesesimone.taxreport.metadata;

import pugliesesimone.taxreport.exception.ConfigurationException;
import pugliesesimone.taxreport.exception.PersonNotFoundException;
import pugliesesimone.taxreport.exception.StorageException;
import pugliesesimone.taxreport.model.*;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class MariaDbMetadata implements MetadataInterface {
    private final String connectionString;
    private final String user;
    private final String password;

    public MariaDbMetadata(String hostname, int port, String dbName, String user, String password) {
        this.connectionString = String.format("jdbc:mariadb://%s:%d/%s", hostname, port, dbName);
        this.user = user;
        this.password = password;

        try {
            Class.forName("org.mariadb.jdbc.Driver");
            initDatabase();
        } catch (Exception e) {
            throw new ConfigurationException("Impossibile connettersi al DB Remoto MariaDB", e);
        }
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(connectionString, user, password);
    }

    private void initDatabase() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS persons (
                    id VARCHAR(36) PRIMARY KEY,
                    name VARCHAR(255) NOT NULL,
                    fiscal_code VARCHAR(16) NOT NULL
                )
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS expenses (
                    id VARCHAR(36) PRIMARY KEY,
                    year VARCHAR(4) NOT NULL,
                    person_id VARCHAR(36) NOT NULL,
                    type VARCHAR(50) NOT NULL,
                    description TEXT,
                    raw_date VARCHAR(20),
                    state VARCHAR(20) NOT NULL,
                    FOREIGN KEY (person_id) REFERENCES persons(id)
                )
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS documents (
                    id VARCHAR(36) PRIMARY KEY,
                    expense_id VARCHAR(36) NOT NULL,
                    doc_type VARCHAR(50) NOT NULL,
                    relative_path VARCHAR(500),
                    FOREIGN KEY (expense_id) REFERENCES expenses(id)
                )
            """);

        } catch (SQLException e) {
            throw new StorageException("Errore DDL Database", e);
        }
    }

    @Override
    public void save(Expense expense) {
        String sqlExpense = """
            REPLACE INTO expenses
            (id, year, person_id, type, description, raw_date, state)
            VALUES (?, ?, ?, ?, ?, ?, ?)""";

        String sqlDeleteDocs = "DELETE FROM documents WHERE expense_id = ?";

        String sqlInsertDoc = """
            INSERT INTO documents (id, expense_id, doc_type, relative_path)
            VALUES (?, ?, ?, ?)""";

        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);

            try (PreparedStatement ps = conn.prepareStatement(sqlExpense)) {
                ps.setString(1, expense.getId().toString());
                ps.setString(2, expense.getYear());
                ps.setString(3, expense.getPerson().getId().toString());
                ps.setString(4, expense.getExpenseType().name());
                ps.setString(5, expense.getDescription());
                ps.setString(6, expense.getRawDate());
                ps.setString(7, expense.getExpenseState().name());
                ps.executeUpdate();
            } catch (SQLException e) {
                // Rollback immediato prima di lanciare eccezione
                try { conn.rollback(); } catch (SQLException ex) { /* Logga errore rollback */ }

                if (e.getErrorCode() == 1452) {
                    throw new PersonNotFoundException("Persona non trovata (FK violation): " + expense.getPerson().getId());
                }
                throw e;
            }

            try (PreparedStatement ps = conn.prepareStatement(sqlDeleteDocs)) {
                ps.setString(1, expense.getId().toString());
                ps.executeUpdate();
            } catch (SQLException e) {
                try { conn.rollback(); } catch (SQLException ex) { /* Logga errore rollback */ }
                throw e;
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
                } catch (SQLException e) {
                    try { conn.rollback(); } catch (SQLException ex) { /* Logga errore rollback */ }
                    throw e;
                }
            }

            conn.commit();

        } catch (SQLException e) {
            throw new StorageException("Errore save su MariaDB", e);
        }
    }

    @Override
    public Optional<Expense> findById(UUID id) {
        String sqlExpense = """
            SELECT e.*, p.name as person_name, p.fiscal_code as person_fc
            FROM expenses e
            JOIN persons p ON e.person_id = p.id
            WHERE e.id = ?""";

        String sqlDocs = "SELECT * FROM documents WHERE expense_id = ?";

        Expense expense = null;

        try (Connection conn = getConnection()) {

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
            throw new StorageException("Errore lettura spesa da MariaDB: " + id, e);
        }
    }
}