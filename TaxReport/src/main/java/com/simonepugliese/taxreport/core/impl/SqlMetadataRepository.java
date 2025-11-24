package com.simonepugliese.taxreport.core.impl;

import com.simonepugliese.taxreport.core.domain.DocumentSlot;
import com.simonepugliese.taxreport.core.domain.ExpenseEntry;
import com.simonepugliese.taxreport.core.dto.DocType;
import com.simonepugliese.taxreport.core.dto.ValidationStatus;
import com.simonepugliese.taxreport.core.exception.StorageException;
import com.simonepugliese.taxreport.core.spi.MetadataRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.lang.reflect.Field;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public class SqlMetadataRepository implements MetadataRepository {
    private static final Logger logger = LoggerFactory.getLogger(SqlMetadataRepository.class);
    private final String connectionString;

    public SqlMetadataRepository(String rootPath) {
        // Il DB sarà un file "taxreport.db" nella root folder
        File dbFile = new File(rootPath, "taxreport.db");
        this.connectionString = "jdbc:sqlite:" + dbFile.getAbsolutePath();
        initDatabase();
    }

    private void initDatabase() {
        try (Connection conn = DriverManager.getConnection(connectionString);
             Statement stmt = conn.createStatement()) {

            // Tabella Spese (Master)
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS expenses (
                    id TEXT PRIMARY KEY,
                    category_id TEXT NOT NULL,
                    date TEXT NOT NULL,
                    description TEXT,
                    physical_path TEXT,
                    status TEXT NOT NULL
                )
            """);

            // Tabella Slot Documenti (Detail)
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS slots (
                    expense_id TEXT NOT NULL,
                    doc_type TEXT NOT NULL,
                    mandatory INTEGER,
                    expected_filename TEXT,
                    is_filled INTEGER,
                    file_name TEXT,
                    file_size INTEGER,
                    uploaded_at TEXT,
                    PRIMARY KEY (expense_id, doc_type),
                    FOREIGN KEY(expense_id) REFERENCES expenses(id)
                )
            """);

        } catch (SQLException e) {
            throw new StorageException("Impossibile inizializzare il database SQLite", e);
        }
    }

    @Override
    public void save(ExpenseEntry entry) {
        String sqlExpense = "INSERT OR REPLACE INTO expenses (id, category_id, date, description, physical_path, status) VALUES (?, ?, ?, ?, ?, ?)";
        String sqlDeleteSlots = "DELETE FROM slots WHERE expense_id = ?";
        String sqlInsertSlot = "INSERT INTO slots (expense_id, doc_type, mandatory, expected_filename, is_filled, file_name, file_size, uploaded_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = DriverManager.getConnection(connectionString)) {
            conn.setAutoCommit(false); // Inizio Transazione

            // 1. Salva Header Spesa
            try (PreparedStatement ps = conn.prepareStatement(sqlExpense)) {
                ps.setString(1, entry.getId().toString());
                ps.setString(2, entry.getCategoryId());
                ps.setString(3, entry.getDate().toString());
                ps.setString(4, entry.getSanitizedDescription());
                ps.setString(5, entry.getPhysicalPath());
                ps.setString(6, entry.getStatus().name());
                ps.executeUpdate();
            }

            // 2. Pulisci vecchi slot per questo ID (Full Refresh)
            try (PreparedStatement ps = conn.prepareStatement(sqlDeleteSlots)) {
                ps.setString(1, entry.getId().toString());
                ps.executeUpdate();
            }

            // 3. Inserisci Slot attuali
            try (PreparedStatement ps = conn.prepareStatement(sqlInsertSlot)) {
                for (DocumentSlot slot : entry.getSlots().values()) {
                    ps.setString(1, entry.getId().toString());
                    ps.setString(2, slot.getType().name());
                    ps.setInt(3, slot.isMandatory() ? 1 : 0);
                    ps.setString(4, slot.getExpectedFilename());

                    if (slot.isFilled()) {
                        var fileMeta = slot.getCurrentFile();
                        ps.setInt(5, 1);
                        ps.setString(6, fileMeta.filename());
                        ps.setLong(7, fileMeta.size());
                        ps.setString(8, fileMeta.uploadedAt().toString());
                    } else {
                        ps.setInt(5, 0);
                        ps.setNull(6, Types.VARCHAR);
                        ps.setNull(7, Types.INTEGER);
                        ps.setNull(8, Types.VARCHAR);
                    }
                    ps.addBatch();
                }
                ps.executeBatch();
            }

            conn.commit(); // Conferma Transazione

        } catch (SQLException e) {
            throw new StorageException("Errore salvataggio spesa su SQL: " + entry.getId(), e);
        }
    }

    @Override
    public Optional<ExpenseEntry> findById(UUID id) {
        String sqlExpense = "SELECT * FROM expenses WHERE id = ?";
        String sqlSlots = "SELECT * FROM slots WHERE expense_id = ?";

        ExpenseEntry entry = null;

        try (Connection conn = DriverManager.getConnection(connectionString)) {
            // 1. Carica Header
            try (PreparedStatement ps = conn.prepareStatement(sqlExpense)) {
                ps.setString(1, id.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        entry = new ExpenseEntry(
                                UUID.fromString(rs.getString("id")),
                                rs.getString("category_id"),
                                LocalDate.parse(rs.getString("date")),
                                rs.getString("description")
                        );
                        entry.setPhysicalPath(rs.getString("physical_path"));
                        entry.setStatus(ValidationStatus.valueOf(rs.getString("status")));
                    } else {
                        return Optional.empty();
                    }
                }
            }

            // 2. Carica Slots e Idrata
            try (PreparedStatement ps = conn.prepareStatement(sqlSlots)) {
                ps.setString(1, id.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        DocType type = DocType.valueOf(rs.getString("doc_type"));
                        boolean mandatory = rs.getInt("mandatory") == 1;
                        String expectedFilename = rs.getString("expected_filename");

                        DocumentSlot slot = new DocumentSlot(type, mandatory, expectedFilename);

                        if (rs.getInt("is_filled") == 1) {
                            String filename = rs.getString("file_name");
                            long size = rs.getLong("file_size");
                            LocalDateTime uploadedAt = LocalDateTime.parse(rs.getString("uploaded_at"));

                            // Hack: Usiamo Reflection per settare il record FileMetadata privato
                            // senza dover sporcare la classe di dominio con setter pubblici o costruttori strani
                            hydrateSlot(slot, filename, size, uploadedAt);
                        }
                        entry.addSlot(slot);
                    }
                }
            }

            return Optional.of(entry);

        } catch (SQLException e) {
            throw new StorageException("Errore lettura spesa da SQL: " + id, e);
        }
    }

    @Override
    public void updateStatus(UUID id, ValidationStatus status) {
        String sql = "UPDATE expenses SET status = ? WHERE id = ?";
        try (Connection conn = DriverManager.getConnection(connectionString);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status.name());
            ps.setString(2, id.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new StorageException("Errore aggiornamento stato SQL: " + id, e);
        }
    }

    // Metodo helper per iniettare i metadati nel dominio senza rompere l'incapsulamento
    private void hydrateSlot(DocumentSlot slot, String filename, long size, LocalDateTime uploadedAt) {
        try {
            // Creiamo il record interno (FileMetadata è statico dentro DocumentSlot)
            DocumentSlot.FileMetadata metadata = new DocumentSlot.FileMetadata(filename, size, uploadedAt);

            Field field = DocumentSlot.class.getDeclaredField("currentFile");
            field.setAccessible(true);
            field.set(slot, metadata);
        } catch (Exception e) {
            logger.warn("Impossibile idratare slot via reflection", e);
        }
    }
}