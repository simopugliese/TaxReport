package pugliesesimone.taxreport.storage;

import java.io.InputStream;

public interface StorageInterface {
    boolean createFolder(String relativePath);
    boolean existsFolder(String relativePath);
    boolean saveFile(String relativePath, String filename, InputStream inputStream);
    InputStream loadFile(String relativePath, String filename);
    boolean deleteFile(String relativePath, String filename);
}
