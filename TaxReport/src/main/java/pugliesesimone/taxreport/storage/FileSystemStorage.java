package pugliesesimone.taxreport.storage;

import pugliesesimone.taxreport.exception.StorageException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

public class FileSystemStorage implements StorageInterface {
    private final Path rootParams;

    public FileSystemStorage(String rootPath) {
        this.rootParams = Path.of(rootPath);
    }

    @Override
    public boolean createFolder(String relativePath) {
        Path fullPath = resolve(relativePath);
        if (Files.exists(fullPath)) {
            return false;
        }
        try {
            Files.createDirectories(fullPath);
            return true;
        } catch (IOException e) {
            throw new StorageException("Errore creazione directory locale", e);
        }
    }

    @Override
    public boolean existsFolder(String relativePath) {
        return Files.exists(resolve(relativePath));
    }

    @Override
    public boolean saveFile(String relativePath, String filename, InputStream inputStream) {
        Path folder = resolve(relativePath);
        Path target = folder.resolve(filename);
        try {
            Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException e) {
            throw new StorageException("Errore salvataggio file locale", e);
        }
    }

    @Override
    public InputStream loadFile(String relativePath, String filename) {
        Path folder = resolve(relativePath);
        Path target = folder.resolve(filename);
        try {
            return Files.newInputStream(target, StandardOpenOption.READ);
        } catch (IOException e) {
            throw new StorageException("Errore lettura file", e);
        }
    }

    @Override
    public boolean deleteFile(String relativePath, String filename) {
        Path folder = resolve(relativePath);
        Path target = folder.resolve(filename);
        try {
            Files.deleteIfExists(target);
            return true;
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
