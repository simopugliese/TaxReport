package pugliesesimone.taxreport.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pugliesesimone.taxreport.exception.StorageException;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class FileSystemStorageTest {

    @TempDir
    Path tempDir;

    FileSystemStorage storage;

    @BeforeEach
    void setUp() {
        storage = new FileSystemStorage(tempDir.toString());
    }

    @Test
    void saveFile_ShouldWriteContentToDisk() throws Exception {
        // Arrange
        String content = "Hello Tax Report";
        InputStream is = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));

        // [FIX] Dobbiamo creare la cartella prima di scriverci dentro!
        // Nel codice reale lo fa il TaxReportService, qui dobbiamo farlo a mano.
        storage.createFolder("2024/DOCS");

        // Act
        boolean result = storage.saveFile("2024/DOCS", "test.txt", is);

        // Assert
        assertTrue(result);
        Path savedFile = tempDir.resolve("2024/DOCS/test.txt");
        assertTrue(savedFile.toFile().exists());
    }

    @Test
    void loadFile_ShouldReadContentBack() throws Exception {
        // Qui usiamo "." (root), che esiste sempre, quindi non serve createFolder
        String content = "Dati Importanti";
        storage.saveFile(".", "leggi_mi.txt", new ByteArrayInputStream(content.getBytes()));

        // Act
        InputStream is = storage.loadFile(".", "leggi_mi.txt");
        String readContent = new String(is.readAllBytes());

        // Assert
        assertEquals(content, readContent);
    }

    @Test
    void createFolder_ShouldCreateDirectoryHierarchy() {
        boolean created = storage.createFolder("A/B/C");

        assertTrue(created);
        assertTrue(tempDir.resolve("A/B/C").toFile().isDirectory());
    }

    @Test
    void deleteFile_ShouldRemoveFile() {
        storage.saveFile(".", "temp.txt", new ByteArrayInputStream("x".getBytes()));
        assertTrue(tempDir.resolve("temp.txt").toFile().exists());

        boolean deleted = storage.deleteFile(".", "temp.txt");

        assertTrue(deleted);
        assertFalse(tempDir.resolve("temp.txt").toFile().exists());
    }

    @Test
    void security_ShouldBlockPathTraversal() {
        assertThrows(StorageException.class, () -> {
            storage.saveFile("../", "hacker.txt", new ByteArrayInputStream("x".getBytes()));
        });
    }
}