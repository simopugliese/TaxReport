package com.simonepugliese.taxreport.core.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class FileSystemStorageStrategyTest {

    @TempDir
    Path tempDir; // JUnit crea e distrugge questa cartella automaticamente

    @Test
    void testCreateDirectoryAndExists() {
        FileSystemStorageStrategy storage = new FileSystemStorageStrategy(tempDir.toString());
        String relativePath = "/2024/TestUser";

        // 1. Verifica che non esista all'inizio
        assertFalse(storage.exists(relativePath));

        // 2. Crea la directory
        boolean created = storage.createDirectory(relativePath);
        assertTrue(created, "La directory dovrebbe essere creata");
        assertTrue(storage.exists(relativePath));
        assertTrue(Files.exists(tempDir.resolve("2024/TestUser")));

        // 3. Riprova a creare (deve ritornare false perché esiste già)
        boolean createdAgain = storage.createDirectory(relativePath);
        assertFalse(createdAgain, "Non dovrebbe ricreare una directory esistente");
    }

    @Test
    void testSaveFile() throws IOException {
        FileSystemStorageStrategy storage = new FileSystemStorageStrategy(tempDir.toString());
        String folder = "Docs";
        String filename = "test.txt";
        String content = "Hello World";

        storage.createDirectory(folder);

        // WHEN
        storage.saveFile(folder, filename, new ByteArrayInputStream(content.getBytes()));

        // THEN
        Path savedFile = tempDir.resolve(folder).resolve(filename);
        assertTrue(Files.exists(savedFile));
        assertEquals(content, Files.readString(savedFile));
    }
}