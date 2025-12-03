package pugliesesimone.taxreport.metadata;

import pugliesesimone.taxreport.exception.ConfigurationException;
import pugliesesimone.taxreport.exception.PersonNotFoundException;
import pugliesesimone.taxreport.exception.StorageException;
import pugliesesimone.taxreport.model.*;

import java.sql.*;
import java.util.*;
import java.util.UUID;

public class MariaDbMetadata implements MetadataInterface {

    private static final String SQL_STATE_INTEGRITY_VIOLATION = "23";

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
        } catch (ClassNotFoundException e) {
            throw new ConfigurationException("Driver MariaDB non trovato nel classpath", e);
        } catch (Exception e) {
            throw new ConfigurationException("Impossibile connettersi o inizializzare il DB MariaDB", e);
        }
    }

    protected Connection getConnection() throws SQLException {
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
    public void savePerson(Person person) {
        String sql = "INSERT INTO persons (id, name, fiscal_code) VALUES (?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE name = VALUES(name), fiscal_code = VALUES(fiscal_code)";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, person.getId().toString());
            ps.setString(2, person.getName());
            ps.setString(3, person.getFiscalCode());
            ps.executeUpdate();

        } catch (SQLException e) {
            throw new StorageException("Errore salvataggio persona: " + person.getName(), e);
        }
    }

    @Override
    public List<Person> findAllPersons() {
        List<Person> persons = new ArrayList<>();
        String sql = "SELECT * FROM persons ORDER BY name ASC";
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                persons.add(new Person(
                        UUID.fromString(rs.getString("id")),
                        rs.getString("name"),
                        rs.getString("fiscal_code")
                ));
            }
        } catch (SQLException e) {
            throw new StorageException("Errore caricamento persone", e);
        }
        return persons;
    }

    @Override
    public void save(Expense expense) {
        String sqlExpense = """
            INSERT INTO expenses (id, year, person_id, type, description, raw_date, state)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                year = VALUES(year),
                person_id = VALUES(person_id),
                type = VALUES(type),
                description = VALUES(description),
                raw_date = VALUES(raw_date),
                state = VALUES(state)
            """;

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
                try { conn.rollback(); } catch (SQLException ex) { /* Log */ }
                String state = e.getSQLState();
                if (state != null && state.startsWith(SQL_STATE_INTEGRITY_VIOLATION)) {
                    throw new PersonNotFoundException("Persona non trovata (FK violation): " + expense.getPerson().getId());
                }
                throw e;
            }

            try (PreparedStatement ps = conn.prepareStatement(sqlDeleteDocs)) {
                ps.setString(1, expense.getId().toString());
                ps.executeUpdate();
            } catch (SQLException e) {
                try { conn.rollback(); } catch (SQLException ex) { /* Log */ }
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
                    try { conn.rollback(); } catch (SQLException ex) { /* Log */ }
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
        String sql = """
            SELECT e.*, p.name as person_name, p.fiscal_code as person_fc
            FROM expenses e
            JOIN persons p ON e.person_id = p.id
            WHERE e.id = ?""";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Expense e = mapRowToExpense(rs);
                    loadDocumentsForExpense(conn, e);
                    return Optional.of(e);
                }
            }
        } catch (SQLException e) {
            throw new StorageException("Errore findById", e);
        }
        return Optional.empty();
    }

    // [OPTIMIZED] Risolto problema N+1 con una singola query JOIN
    @Override
    public List<Expense> findByYear(String year) {
        // Usiamo una mappa per raggruppare i documenti sotto la stessa spesa
        // LinkedHashMap mantiene l'ordine di inserimento (quindi quello della query)
        Map<UUID, Expense> expenseMap = new LinkedHashMap<>();

        String sql = """
            SELECT 
                e.id AS e_id, e.year, e.type, e.description, e.raw_date, e.state,
                p.id AS p_id, p.name AS p_name, p.fiscal_code AS p_fc,
                d.id AS d_id, d.doc_type, d.relative_path
            FROM expenses e
            JOIN persons p ON e.person_id = p.id
            LEFT JOIN documents d ON e.id = d.expense_id
            WHERE e.year = ?
            ORDER BY e.id DESC
        """;

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, year);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    UUID expenseId = UUID.fromString(rs.getString("e_id"));

                    // Se la spesa è già nella mappa, usala; altrimenti creala
                    Expense expense = expenseMap.computeIfAbsent(expenseId, k -> {
                        try {
                            Person person = new Person(
                                    UUID.fromString(rs.getString("p_id")),
                                    rs.getString("p_name"),
                                    rs.getString("p_fc")
                            );
                            return new Expense(
                                    k,
                                    rs.getString("year"),
                                    person,
                                    ExpenseType.valueOf(rs.getString("type")),
                                    rs.getString("description"),
                                    rs.getString("raw_date"),
                                    ExpenseState.valueOf(rs.getString("state"))
                            );
                        } catch (SQLException e) {
                            throw new RuntimeException("Errore mapping resultSet", e);
                        }
                    });

                    // Se c'è un documento (LEFT JOIN non nullo), aggiungilo
                    String docIdStr = rs.getString("d_id");
                    if (docIdStr != null) {
                        Document doc = new Document(
                                UUID.fromString(docIdStr),
                                DocumentType.valueOf(rs.getString("doc_type")),
                                rs.getString("relative_path")
                        );
                        expense.addDocument(doc);
                    }
                }
            }
        } catch (SQLException e) {
            throw new StorageException("Errore ricerca spese per anno: " + year, e);
        } catch (RuntimeException e) {
            if (e.getCause() instanceof SQLException) {
                throw new StorageException("Errore SQL durante il mapping", e.getCause());
            }
            throw e;
        }

        return new ArrayList<>(expenseMap.values());
    }

    @Override
    public List<String> getAvailableYears() {
        List<String> years = new ArrayList<>();
        String sql = "SELECT DISTINCT year FROM expenses ORDER BY year DESC";
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while(rs.next()) {
                years.add(rs.getString("year"));
            }
        } catch (SQLException e) {
            throw new StorageException("Errore caricamento anni disponibili", e);
        }
        return years;
    }

    // Helper usati da findById (rimasti per compatibilità)
    private Expense mapRowToExpense(ResultSet rs) throws SQLException {
        Person person = new Person(
                UUID.fromString(rs.getString("person_id")),
                rs.getString("person_name"),
                rs.getString("person_fc")
        );
        return new Expense(
                UUID.fromString(rs.getString("id")),
                rs.getString("year"),
                person,
                ExpenseType.valueOf(rs.getString("type")),
                rs.getString("description"),
                rs.getString("raw_date"),
                ExpenseState.valueOf(rs.getString("state"))
        );
    }

    private void loadDocumentsForExpense(Connection conn, Expense expense) throws SQLException {
        String sql = "SELECT * FROM documents WHERE expense_id = ?";
        List<Document> docs = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, expense.getId().toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    docs.add(new Document(
                            UUID.fromString(rs.getString("id")),
                            DocumentType.valueOf(rs.getString("doc_type")),
                            rs.getString("relative_path")
                    ));
                }
            }
        }
        expense.setDocuments(docs);
    }
}