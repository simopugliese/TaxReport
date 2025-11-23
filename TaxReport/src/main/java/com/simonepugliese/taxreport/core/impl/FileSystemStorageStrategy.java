package com.simonepugliese.taxreport.core.impl;

import com.simonepugliese.taxreport.core.exception.StorageException;
import com.simonepugliese.taxreport.core.spi.StorageStrategy;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class FileSystemStorageStrategy implements StorageStrategy {
    private final Path rootParams;

    public FileSystemStorageStrategy(String rootPath) {
        this.rootParams = Path.of(rootPath);
    }

    @Override
    public boolean createDirectory(String relativePath) {
        Path fullPath = resolve(relativePath);
        if (Files.exists(fullPath)) return false;
        try {
            Files.createDirectories(fullPath);
            return true;
        } catch (IOException e) {
            throw new StorageException("Errore creazione directory locale", e);
        }
    }

    @Override
    public boolean exists(String relativePath) {
        return Files.exists(resolve(relativePath));
    }

    @Override
    public void saveFile(String relativePathFolder, String filename, InputStream content) {
        Path folder = resolve(relativePathFolder);
        Path target = folder.resolve(filename);
        try {
            Files.copy(content, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new StorageException("Errore salvataggio file locale", e);
        }
    }

    @Override
    public void deleteFile(String relativePathFolder, String filename) {
        Path folder = resolve(relativePathFolder);
        Path target = folder.resolve(filename);
        try {
            Files.deleteIfExists(target);
        } catch (IOException e) {
            throw new StorageException("Errore durante cancellazione file: " + filename, e);
        }
    }

    private Path resolve(String relative) {
        if (relative.startsWith("/") || relative.startsWith("\\")) {
            relative = relative.substring(1);
        }
        return rootParams.resolve(relative);
    }
}