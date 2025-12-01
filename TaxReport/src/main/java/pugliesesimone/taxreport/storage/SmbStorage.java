package pugliesesimone.taxreport.storage;

import com.hierynomus.msdtyp.AccessMask;
import com.hierynomus.mssmb2.SMB2CreateDisposition;
import com.hierynomus.mssmb2.SMB2ShareAccess;
import com.hierynomus.smbj.SMBClient;
import com.hierynomus.smbj.auth.AuthenticationContext;
import com.hierynomus.smbj.connection.Connection;
import com.hierynomus.smbj.session.Session;
import com.hierynomus.smbj.share.DiskShare;
import com.hierynomus.smbj.share.File;
import pugliesesimone.taxreport.exception.StorageException;

import java.io.IOException;
import java.io.InputStream;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

public class SmbStorage implements StorageInterface {

    private static final int BUFFER_SIZE = 8192;

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

                byte[] buffer = new byte[BUFFER_SIZE];
                int bytesRead;
                long fileOffset = 0;

                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    file.write(buffer, fileOffset, 0, bytesRead);
                    fileOffset += bytesRead;
                }
                return true;
            }
        });
    }

    @Override
    public InputStream loadFile(String relativePath, String filename) {
        Connection connection = null;
        Session session = null;
        DiskShare share = null;
        File file = null;
        boolean success = false;

        try {
            connection = client.connect(hostname);
            AuthenticationContext ac = new AuthenticationContext(auth.getUsername(), auth.getPassword(), auth.getDomain());
            session = connection.authenticate(ac);
            share = (DiskShare) session.connectShare(shareName);

            String fullPath = normalizePath(relativePath + "/" + filename);
            if (!share.fileExists(fullPath)) {
                throw new StorageException("File non trovato su SMB: " + fullPath, null);
            }

            Set<AccessMask> accessMask = new HashSet<>(EnumSet.of(AccessMask.GENERIC_READ));
            Set<SMB2ShareAccess> shareAccess = new HashSet<>(EnumSet.of(SMB2ShareAccess.FILE_SHARE_READ));

            file = share.openFile(fullPath, accessMask, null, shareAccess, SMB2CreateDisposition.FILE_OPEN, null);

            // Passiamo le risorse allo stream che ne diventerà responsabile
            SmbFileInputStream stream = new SmbFileInputStream(connection, session, share, file);
            success = true;
            return stream;

        } catch (Exception e) {
            if (e instanceof StorageException) {
                throw (StorageException) e;
            }
            throw new StorageException("Errore apertura stream SMB", e);
        } finally {
            // Resource Leak Fix: Se non siamo arrivati alla creazione dello stream (success=false),
            // dobbiamo chiudere manualmente tutto ciò che è stato aperto finora.
            if (!success) {
                if (file != null) try { file.close(); } catch (Exception ignored) {}
                if (share != null) try { share.close(); } catch (Exception ignored) {}
                if (session != null) try { session.close(); } catch (Exception ignored) {}
                if (connection != null) try { connection.close(); } catch (Exception ignored) {}
            }
        }
    }

    private static class SmbFileInputStream extends InputStream {
        private final Connection connection;
        private final Session session;
        private final DiskShare share;
        private final File file;
        private long offset = 0;

        public SmbFileInputStream(Connection c, Session s, DiskShare sh, File f) {
            this.connection = c;
            this.session = s;
            this.share = sh;
            this.file = f;
        }

        @Override
        public int read() throws IOException {
            byte[] b = new byte[1];
            int read = read(b, 0, 1);
            return read == -1 ? -1 : b[0] & 0xFF;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int actualRead = file.read(b, offset, off, len);
            if (actualRead > 0) {
                offset += actualRead;
                return actualRead;
            }
            return -1;
        }

        @Override
        public void close() throws IOException {
            try { file.close(); } catch (Exception ignored) {}
            try { share.close(); } catch (Exception ignored) {}
            try { session.close(); } catch (Exception ignored) {}
            try { connection.close(); } catch (Exception ignored) {}
        }
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