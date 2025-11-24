package pugliesesimone.taxreport.storage;

import com.hierynomus.msdtyp.AccessMask;
import com.hierynomus.msfscc.FileAttributes;
import com.hierynomus.mssmb2.SMB2CreateDisposition;
import com.hierynomus.mssmb2.SMB2ShareAccess;
import com.hierynomus.smbj.SMBClient;
import com.hierynomus.smbj.auth.AuthenticationContext;
import com.hierynomus.smbj.connection.Connection;
import com.hierynomus.smbj.session.Session;
import com.hierynomus.smbj.share.DiskShare;
import com.hierynomus.smbj.share.File;
import pugliesesimone.taxreport.exception.StorageException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

public class SmbStorage implements StorageInterface {
    private final String hostname;
    private final String shareName;
    private final AuthenticationContext auth;
    private final SMBClient client;

    public SmbStorage(String hostname, String shareName, String username, String password) {
        this.hostname = hostname;
        this.shareName = shareName;
        this.auth = new AuthenticationContext(username, password.toCharArray(), null);
        this.client = new SMBClient();
    }

    @Override
    public boolean createFolder(String relativePath) {
        return execute(share -> {
            String path = normalizePath(relativePath);
            if (share.folderExists(path)) {
                return false;
            }
            mkdirs(share, path);
            return true;
        });
    }

    @Override
    public boolean existsFolder(String relativePath) {
        return execute(share -> share.folderExists(normalizePath(relativePath)));
    }

    @Override
    public boolean saveFile(String relativePath, String filename, InputStream inputStream) {
        return execute(share -> {
            String fullPath = normalizePath(relativePath + "/" + filename);

            if (!share.folderExists(normalizePath(relativePath))) {
                mkdirs(share, normalizePath(relativePath));
            }

            Set<AccessMask> accessMask = new HashSet<>(EnumSet.of(AccessMask.GENERIC_WRITE));
            Set<SMB2ShareAccess> shareAccess = new HashSet<>(EnumSet.of(SMB2ShareAccess.FILE_SHARE_WRITE));

            try (File file = share.openFile(
                    fullPath, accessMask, null, shareAccess,
                    SMB2CreateDisposition.FILE_OVERWRITE_IF, null)) {

                byte[] buffer = inputStream.readAllBytes();
                file.write(buffer, 0);
                return true;
            }
        });
    }

    @Override
    public InputStream loadFile(String relativePath, String filename) {
        return execute(share -> {
            String fullPath = normalizePath(relativePath + "/" + filename);
            if (!share.fileExists(fullPath)) {
                throw new StorageException("File non trovato su SMB: " + fullPath, null);
            }

            Set<AccessMask> accessMask = new HashSet<>(EnumSet.of(AccessMask.GENERIC_READ));
            Set<SMB2ShareAccess> shareAccess = new HashSet<>(EnumSet.of(SMB2ShareAccess.FILE_SHARE_READ));

            try (File file = share.openFile(
                    fullPath, accessMask, null, shareAccess,
                    SMB2CreateDisposition.FILE_OPEN, null)) {

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                file.read(baos);
                return new ByteArrayInputStream(baos.toByteArray());
            } catch (IOException e) {
                throw new StorageException("Errore lettura stream SMB", e);
            }
        });
    }

    @Override
    public boolean deleteFile(String relativePath, String filename) {
        return execute(share -> {
            String fullPath = normalizePath(relativePath + "/" + filename);
            if (share.fileExists(fullPath)) {
                share.rm(fullPath);
                return true;
            }
            return false;
        });
    }

    private <T> T execute(SmbAction<T> action) {
        try (Connection connection = client.connect(hostname)) {
            AuthenticationContext ac = new AuthenticationContext(auth.getUsername(), auth.getPassword(), auth.getDomain());
            Session session = connection.authenticate(ac);

            try (DiskShare share = (DiskShare) session.connectShare(shareName)) {
                return action.run(share);
            }
        } catch (Exception e) {
            throw new StorageException("Errore operazione SMB su " + hostname, e);
        }
    }

    private void mkdirs(DiskShare share, String path) {
        String[] parts = path.split("\\\\");
        String currentPath = "";
        for (String part : parts) {
            if (part.isEmpty()) continue;
            currentPath += (currentPath.isEmpty() ? "" : "\\") + part;

            if (!share.folderExists(currentPath)) {
                share.mkdir(currentPath);
            }
        }
    }

    private String normalizePath(String path) {
        if (path.startsWith("/") || path.startsWith("\\")) {
            path = path.substring(1);
        }
        return path.replace('/', '\\');
    }

    @FunctionalInterface
    private interface SmbAction<T> {
        T run(DiskShare share) throws IOException;
    }
}