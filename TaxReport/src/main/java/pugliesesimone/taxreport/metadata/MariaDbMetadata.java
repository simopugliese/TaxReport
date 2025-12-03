package pugliesesimone.taxreport.metadata;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import pugliesesimone.taxreport.exception.ConfigurationException;
import pugliesesimone.taxreport.exception.PersonNotFoundException;
import pugliesesimone.taxreport.exception.StorageException;
import pugliesesimone.taxreport.model.*;

import java.sql.*;
import java.util.*;
import java.util.UUID;

public class MariaDbMetadata implements MetadataInterface, AutoCloseable {

    private static final String SQL_STATE_INTEGRITY_VIOLATION = "23";
    private final HikariDataSource dataSource;

    public MariaDbMetadata(String hostname, int port, String dbName, String user, String password) {
        try {
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(String.format("jdbc:mariadb://%s:%d/%s", hostname, port, dbName));
            config.setUsername(user);
            config.setPassword(password);
            config.setDriverClassName("org.mariadb.jdbc.Driver");

            // Tuning per performance e stabilità
            config.setMaximumPoolSize(10);
            config.setMinimumIdle(2);
            config.setIdleTimeout(60000);
            config.setConnectionTimeout(5000);
            config.setPoolName("TaxReportPool");

            this.dataSource = new HikariDataSource(config);
            initDatabase();
        } catch (Exception e) {
            throw new ConfigurationException("Impossibile inizializzare HikariCP Pool", e);
        }
    }

    protected MariaDbMetadata() {
        this.dataSource = null;
    }

    protected Connection getConnection() throws SQLException {
        if (dataSource == null) throw new SQLException("DataSource non inizializzato (Testing Mode)");
        return dataSource.getConnection();
    }

    @Override
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
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
        // Implementazione singola (delega a una lista di 1 per coerenza, o mantieni logica originale)
        // Per semplicità e robustezza manteniamo la logica originale per il singolo save,
        // che gestisce anche i documenti.
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
        String sqlInsertDoc = "INSERT INTO documents (id, expense_id, doc_type, relative_path) VALUES (?, ?, ?, ?)";

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
                try { conn.rollback(); } catch (SQLException ex) { }
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
                try { conn.rollback(); } catch (SQLException ex) { }
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
                    try { conn.rollback(); } catch (SQLException ex) { }
                    throw e;
                }
            }

            conn.commit();

        } catch (SQLException e) {
            throw new StorageException("Errore save su MariaDB", e);
        }
    }

    @Override
    public void saveAll(List<Expense> expenses) {
        if (expenses == null || expenses.isEmpty()) return;

        // Query ottimizzata per batch update dello stato (scenario principale del Compliance Check)
        // Se serve aggiornare tutto, basta aggiungere gli altri campi nel SET
        String sqlExpense = """
            INSERT INTO expenses (id, year, person_id, type, description, raw_date, state)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                state = VALUES(state)
            """;

        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);

            try (PreparedStatement ps = conn.prepareStatement(sqlExpense)) {
                for (Expense expense : expenses) {
                    ps.setString(1, expense.getId().toString());
                    ps.setString(2, expense.getYear());
                    ps.setString(3, expense.getPerson().getId().toString());
                    ps.setString(4, expense.getExpenseType().name());
                    ps.setString(5, expense.getDescription());
                    ps.setString(6, expense.getRawDate());
                    ps.setString(7, expense.getExpenseState().name());
                    ps.addBatch();
                }
                ps.executeBatch();
                conn.commit();
            } catch (SQLException e) {
                try { conn.rollback(); } catch (SQLException ex) { }
                throw e;
            }
        } catch (SQLException e) {
            throw new StorageException("Errore Batch SaveAll su MariaDB", e);
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

    @Override
    public List<Expense> findByYear(String year) {
        // Implementazione originale (senza limiti)
        // Nota: Potrebbe essere inefficiente con molti dati, ma è richiesto dall'interfaccia
        return findByYearInternal(year, -1, -1);
    }

    @Override
    public List<Expense> findByYear(String year, int limit, int offset) {
        return findByYearInternal(year, limit, offset);
    }

    private List<Expense> findByYearInternal(String year, int limit, int offset) {
        Map<UUID, Expense> expenseMap = new LinkedHashMap<>();

        StringBuilder sqlBuilder = new StringBuilder("""
            SELECT 
                e.id AS e_id, e.year, e.type, e.description, e.raw_date, e.state,
                p.id AS p_id, p.name AS p_name, p.fiscal_code AS p_fc
            FROM expenses e
            JOIN persons p ON e.person_id = p.id
            WHERE e.year = ?
            ORDER BY e.id DESC
        """);

        if (limit > 0) {
            sqlBuilder.append(" LIMIT ? OFFSET ?");
        }

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sqlBuilder.toString())) {

            ps.setString(1, year);
            if (limit > 0) {
                ps.setInt(2, limit);
                ps.setInt(3, offset);
            }

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    // Mapping base Expense (senza duplicazione di logica)
                    Expense expense = mapRowToExpense(rs);
                    expenseMap.put(expense.getId(), expense);
                }
            }

            // Ottimizzazione: Caricamento documenti in batch per le spese trovate
            // (Evita il problema N+1 select se possibile, qui semplificato)
            if (!expenseMap.isEmpty()) {
                loadDocumentsForExpenses(conn, expenseMap);
            }

        } catch (SQLException e) {
            throw new StorageException("Errore ricerca spese per anno: " + year, e);
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

    private Expense mapRowToExpense(ResultSet rs) throws SQLException {
        Person person = new Person(
                UUID.fromString(rs.getString("p_id") != null ? rs.getString("p_id") : rs.getString("person_id")),
                rs.getString("p_name") != null ? rs.getString("p_name") : rs.getString("person_name"),
                rs.getString("p_fc") != null ? rs.getString("p_fc") : rs.getString("person_fc")
        );
        String idStr = rs.getString("e_id") != null ? rs.getString("e_id") : rs.getString("id");
        return new Expense(
                UUID.fromString(idStr),
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

    private void loadDocumentsForExpenses(Connection conn, Map<UUID, Expense> expenseMap) throws SQLException {
        if (expenseMap.isEmpty()) return;

        // Costruiamo una clausola IN (...) dinamica
        // Nota: Se la pagina è grande (es. 1000), meglio fare più query o usare tabelle temporanee,
        // ma per 50-100 elementi va bene.
        StringBuilder inClause = new StringBuilder();
        for (int i = 0; i < expenseMap.size(); i++) inClause.append("?,");
        inClause.setLength(inClause.length() - 1); // Rimuovi ultima virgola

        String sql = "SELECT * FROM documents WHERE expense_id IN (" + inClause + ")";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            int index = 1;
            for (UUID id : expenseMap.keySet()) {
                ps.setString(index++, id.toString());
            }

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    UUID expenseId = UUID.fromString(rs.getString("expense_id"));
                    Expense exp = expenseMap.get(expenseId);
                    if (exp != null) {
                        exp.addDocument(new Document(
                                UUID.fromString(rs.getString("id")),
                                DocumentType.valueOf(rs.getString("doc_type")),
                                rs.getString("relative_path")
                        ));
                    }
                }
            }
        }
    }
}