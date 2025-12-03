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
import java.util.concurrent.locks.ReentrantLock;

public class SmbStorage implements StorageInterface, AutoCloseable {

    private static final int BUFFER_SIZE = 8192;

    private final String hostname;
    private final String shareName;
    private final AuthenticationContext auth;
    private final SMBClient client;

    // Connection caching
    private Connection cachedConnection;
    private Session cachedSession;
    private DiskShare cachedShare;
    private final ReentrantLock lock = new ReentrantLock();

    public SmbStorage(String hostname, String shareName, String username, String password) {
        this.hostname = hostname;
        this.shareName = shareName;
        this.auth = new AuthenticationContext(username, password.toCharArray(), null);
        this.client = new SMBClient(); // Config default
    }

    // Lazy initialization & Reconnection Logic
    private DiskShare getShare() {
        lock.lock();
        try {
            if (cachedConnection == null || !cachedConnection.isConnected()) {
                closeResources();
                cachedConnection = client.connect(hostname);
                cachedSession = cachedConnection.authenticate(auth);
                cachedShare = (DiskShare) cachedSession.connectShare(shareName);
            }
            return cachedShare;
        } catch (Exception e) {
            closeResources(); // Cleanup parziale se fallisce
            throw new StorageException("Errore connessione SMB a " + hostname, e);
        } finally {
            lock.unlock();
        }
    }

    private void closeResources() {
        try { if (cachedShare != null) cachedShare.close(); } catch (Exception ignored) {}
        try { if (cachedSession != null) cachedSession.close(); } catch (Exception ignored) {}
        try { if (cachedConnection != null) cachedConnection.close(); } catch (Exception ignored) {}
        cachedShare = null;
        cachedSession = null;
        cachedConnection = null;
    }

    @Override
    public void close() {
        lock.lock();
        try {
            closeResources();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean createFolder(String relativePath) {
        try {
            DiskShare share = getShare();
            String path = normalizePath(relativePath);
            if (share.folderExists(path)) {
                return false;
            }
            mkdirs(share, path);
            return true;
        } catch (Exception e) {
            throw new StorageException("Errore createFolder SMB", e);
        }
    }

    @Override
    public boolean existsFolder(String relativePath) {
        try {
            return getShare().folderExists(normalizePath(relativePath));
        } catch (Exception e) {
            throw new StorageException("Errore existsFolder SMB", e);
        }
    }

    @Override
    public boolean saveFile(String relativePath, String filename, InputStream inputStream) {
        try {
            DiskShare share = getShare();
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
        } catch (Exception e) {
            throw new StorageException("Errore saveFile SMB", e);
        }
    }

    @Override
    public InputStream loadFile(String relativePath, String filename) {
        try {
            // Nota: Non usiamo 'getShare()' qui perché dobbiamo aprire uno stream
            // che vivrà più a lungo di questo metodo.
            // Per evitare problemi di concorrenza complessi sugli stream aperti,
            // apriamo una connessione dedicata SOLO per la lettura (pattern Read-Isolated).
            // Se le performance di lettura sono un problema, si può implementare un pool di connessioni,
            // ma per ora ottimizziamo metadata/write che sono i più frequenti.

            Connection conn = client.connect(hostname);
            Session session = conn.authenticate(auth);
            DiskShare share = (DiskShare) session.connectShare(shareName);

            String fullPath = normalizePath(relativePath + "/" + filename);
            if (!share.fileExists(fullPath)) {
                share.close(); session.close(); conn.close();
                throw new StorageException("File non trovato su SMB: " + fullPath, null);
            }

            Set<AccessMask> accessMask = new HashSet<>(EnumSet.of(AccessMask.GENERIC_READ));
            Set<SMB2ShareAccess> shareAccess = new HashSet<>(EnumSet.of(SMB2ShareAccess.FILE_SHARE_READ));

            File file = share.openFile(fullPath, accessMask, null, shareAccess, SMB2CreateDisposition.FILE_OPEN, null);

            return new SmbFileInputStream(conn, session, share, file);

        } catch (Exception e) {
            throw new StorageException("Errore loadFile SMB", e);
        }
    }

    @Override
    public boolean deleteFile(String relativePath, String filename) {
        try {
            DiskShare share = getShare();
            String fullPath = normalizePath(relativePath + "/" + filename);
            if (share.fileExists(fullPath)) {
                share.rm(fullPath);
                return true;
            }
            return false;
        } catch (Exception e) {
            throw new StorageException("Errore deleteFile SMB", e);
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
}